package com.messaging.integrations.unifydeskemail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.transport.BroadcastEmailCommand;
import com.messaging.broadcast.transport.BroadcastEmailResult;
import com.messaging.broadcast.transport.BroadcastTransportException;
import com.messaging.common.credential.CredentialCipher;
import com.messaging.common.exception.BadRequestException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** UnifyDesk App is one ordinary external API consumer of the independent Email product. */
@Service
@RequiredArgsConstructor
public class UnifyDeskEmailGateway {
  private final UnifyDeskEmailApiClient api;
  private final UnifyDeskEmailFeatureGate gate;
  private final CredentialCipher cipher;
  private final ObjectMapper mapper;

  public record VerifiedDomain(String domain) {}

  public VerifiedDomain inspect(
      String apiKey, Long domainId, String fromAddress) {
    gate.requireEnabled();
    try {
      var project = api.inspectProject(apiKey);
      if (!project.sendingEnabled()) {
        throw new BadRequestException("UnifyDesk Email project sending is not approved");
      }
      var domain = api.inspectDomain(apiKey, domainId);
      String actual = domain.domain().trim().toLowerCase(Locale.ROOT);
      String sender = fromAddress.trim().toLowerCase(Locale.ROOT);
      int at = sender.lastIndexOf('@');
      if (!"VERIFIED".equals(domain.status()) || at < 1
          || !sender.substring(at + 1).equals(actual)) {
        throw new BadRequestException(
            "UnifyDesk Email sender must use a verified sending domain");
      }
      return new VerifiedDomain(actual);
    } catch (BadRequestException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BadRequestException("UnifyDesk Email domain check interrupted");
    } catch (IOException e) {
      throw new BadRequestException("Unable to validate the UnifyDesk Email project and domain");
    }
  }

  public VerifiedDomain inspect(BroadcastSenderConnection connection, String fromAddress) {
    UnifyDeskEmailStoredCredentials credentials = credentials(connection);
    return inspect(credentials.apiKey(), credentials.sendingDomainId(), fromAddress);
  }

  public BroadcastEmailResult send(BroadcastSenderConnection connection,
      BroadcastSender sender, BroadcastEmailCommand command) {
    gate.requireEnabled();
    UnifyDeskEmailStoredCredentials credentials = credentials(connection);
    if (command.correlationId() == null || command.correlationId().isBlank()) {
      throw BroadcastTransportException.permanent(
          "UD_EMAIL_MISSING_IDEMPOTENCY", "Email correlation identifier required", null);
    }
    try {
      var batch = api.submit(
          credentials.apiKey(),
          "ud-app-" + command.correlationId(),
          new UnifyDeskEmailApiClient.BatchRequest(
              credentials.sendingDomainId(),
              sender.getFromAddress(),
              sender.getReplyToAddress(),
              command.subject(),
              command.bodyText(),
              command.bodyHtml(),
              List.of(command.recipient()),
              "ud-app-" + command.correlationId()));
      return new BroadcastEmailResult("ud-email-batch-" + batch.batchId(), null, Instant.now());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw BroadcastTransportException.unknown(
          "UD_EMAIL_INTERRUPTED", "Email API acceptance uncertain", e);
    } catch (IOException e) {
      // Lost HTTP response can occur after the product accepted a durable batch.
      // Never create a new idempotency key automatically on retry.
      throw BroadcastTransportException.unknown(
          "UD_EMAIL_ACCEPTANCE_UNKNOWN", "Email API acceptance uncertain", e);
    }
  }

  private UnifyDeskEmailStoredCredentials credentials(BroadcastSenderConnection connection) {
    if (connection.getProvider() != BroadcastSenderProvider.UNIFYDESK_EMAIL
        || connection.getEncryptedCredentials() == null
        || connection.getEncryptedCredentials().isBlank()) {
      throw new BadRequestException("UnifyDesk Email provider credentials are missing");
    }
    try {
      String decrypted = cipher.decrypt(connection.getEncryptedCredentials());
      var value = mapper.readValue(decrypted, UnifyDeskEmailStoredCredentials.class);
      if (value.apiKey() == null || !value.apiKey().startsWith("ude_live_")
          || value.sendingDomainId() == null || value.sendingDomainId() <= 0) {
        throw new BadRequestException("Invalid UnifyDesk Email credentials");
      }
      return value;
    } catch (BadRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new BadRequestException("Unable to read UnifyDesk Email credentials");
    }
  }
}

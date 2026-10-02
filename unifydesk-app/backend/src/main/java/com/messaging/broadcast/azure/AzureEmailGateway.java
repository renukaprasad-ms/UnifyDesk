package com.messaging.broadcast.azure;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.transport.BroadcastEmailCommand;
import com.messaging.broadcast.transport.BroadcastEmailResult;
import com.messaging.broadcast.transport.BroadcastTransportException;
import com.messaging.common.credential.CredentialCipher;
import com.messaging.common.exception.BadRequestException;
import java.net.URI;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AzureEmailGateway {

  private final CredentialCipher credentialCipher;
  private final ObjectMapper objectMapper;
  private final BroadcastProperties properties;

  public AzureEmailAccountSnapshot inspect(
      BroadcastSenderConnection connection, String fromAddress) {
    requireProvider(connection);
    String endpoint = endpoint(connection);
    String accessKey = accessKey(connection);
    validateEndpoint(endpoint);
    try {
      client(endpoint, accessKey);
      return new AzureEmailAccountSnapshot(
          true, Math.max(0.0, properties.getAzureEmailMaxPerSecondCap()), fromAddress);
    } catch (RuntimeException exception) {
      throw new BadRequestException(
          "Unable to initialize Azure Communication Services Email: " + safeMessage(exception));
    }
  }

  public BroadcastEmailResult send(
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastEmailCommand command) {
    requireProvider(connection);
    String endpoint = endpoint(connection);
    String accessKey = accessKey(connection);

    EmailMessage message = buildMessage(sender, command);

    try {
      SyncPoller<EmailSendResult, EmailSendResult> poller =
          client(endpoint, accessKey).beginSend(message);
      PollResponse<EmailSendResult> response = poller.poll();
      EmailSendResult value = response == null ? null : response.getValue();
      String operationId = value == null ? null : value.getId();
      if (operationId == null || operationId.isBlank()) {
        throw BroadcastTransportException.unknown(
            "AZURE_EMAIL_NO_OPERATION_ID",
            "Azure accepted the request but did not return an operation id",
            null);
      }
      return new BroadcastEmailResult(operationId, null, Instant.now());
    } catch (BroadcastTransportException exception) {
      throw exception;
    } catch (HttpResponseException exception) {
      int status =
          exception.getResponse() == null ? 0 : exception.getResponse().getStatusCode();
      if (status == 429 || status >= 500) {
        throw BroadcastTransportException.retryable(
            "AZURE_EMAIL_HTTP_" + status, safeMessage(exception), exception);
      }
      if (status >= 400) {
        throw BroadcastTransportException.permanent(
            "AZURE_EMAIL_HTTP_" + status, safeMessage(exception), exception);
      }
      throw BroadcastTransportException.unknown(
          "AZURE_EMAIL_SEND_UNKNOWN", safeMessage(exception), exception);
    } catch (RuntimeException exception) {
      throw BroadcastTransportException.unknown(
          "AZURE_EMAIL_SEND_UNKNOWN",
          "Azure Email send outcome could not be confirmed: " + safeMessage(exception),
          exception);
    }
  }

  EmailMessage buildMessage(BroadcastSender sender, BroadcastEmailCommand command) {
    EmailMessage message =
        new EmailMessage()
            .setSenderAddress(sender.getFromAddress())
            .setToRecipients(command.recipient())
            .setSubject(command.subject() == null ? "" : command.subject())
            .setBodyPlainText(command.bodyText());
    if (command.bodyHtml() != null && !command.bodyHtml().isBlank()) {
      message.setBodyHtml(command.bodyHtml());
    }
    if (command.compliance() != null) {
      message.setHeaders(command.compliance().standardOneClickHeaders());
    }
    return message;
  }

  private EmailClient client(String endpoint, String accessKey) {
    return new EmailClientBuilder()
        .endpoint(endpoint)
        .credential(new AzureKeyCredential(accessKey))
        .buildClient();
  }

  private String endpoint(BroadcastSenderConnection connection) {
    if (connection.getProviderConfigJson() == null
        || connection.getProviderConfigJson().isBlank()) {
      throw new BadRequestException("Azure Email endpoint configuration is missing");
    }
    try {
      AzureEmailProviderConfig config =
          objectMapper.readValue(connection.getProviderConfigJson(), AzureEmailProviderConfig.class);
      if (config.endpoint() == null || config.endpoint().isBlank()) {
        throw new BadRequestException("Azure Email endpoint is missing");
      }
      return config.endpoint().trim();
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Unable to read Azure Email provider configuration");
    }
  }

  private String accessKey(BroadcastSenderConnection connection) {
    if (connection.getEncryptedCredentials() == null
        || connection.getEncryptedCredentials().isBlank()) {
      throw new BadRequestException("Azure Email access key is missing");
    }
    try {
      String json = credentialCipher.decrypt(connection.getEncryptedCredentials());
      AzureEmailStoredCredentials credentials =
          objectMapper.readValue(json, AzureEmailStoredCredentials.class);
      if (credentials.accessKey() == null || credentials.accessKey().isBlank()) {
        throw new BadRequestException("Azure Email access key is missing");
      }
      return credentials.accessKey().trim();
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Unable to read Azure Email credentials");
    }
  }

  private void validateEndpoint(String endpoint) {
    try {
      URI uri = URI.create(endpoint);
      String host = uri.getHost();
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || host == null
          || !host.toLowerCase(java.util.Locale.ROOT).endsWith(".communication.azure.com")) {
        throw new IllegalArgumentException();
      }
    } catch (RuntimeException exception) {
      throw new BadRequestException(
          "Azure endpoint must look like https://<resource>.communication.azure.com");
    }
  }

  private void requireProvider(BroadcastSenderConnection connection) {
    if (connection.getProvider() != BroadcastSenderProvider.AZURE_EMAIL) {
      throw new IllegalArgumentException(
          "Azure Email gateway cannot handle provider " + connection.getProvider());
    }
  }

  private String safeMessage(Throwable throwable) {
    String value = throwable.getMessage();
    return value == null || value.isBlank() ? throwable.getClass().getSimpleName() : value;
  }
}

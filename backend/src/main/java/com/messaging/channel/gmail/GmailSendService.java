package com.messaging.channel.gmail;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.gmail.GmailApiClient.SendMessageResponse;
import com.messaging.channel.gmail.dto.GmailSendRequest;
import com.messaging.channel.gmail.dto.GmailSendResponse;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.exception.ServiceUnavailableException;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailSendService {

  private final ChannelConnectionRepository connectionRepository;
  private final GmailTokenService tokenService;
  private final GmailApiClient gmailApiClient;
  private final GmailInboxPersistenceService persistenceService;
  private final GmailDistributedQuotaManager quotaManager;

  public GmailSendResponse send(Long companyId, Long connectionId, GmailSendRequest request) {
    ChannelConnection connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("Gmail connection not found"));

    if (connection.getProviderType() != ProviderType.GMAIL) {
      throw new BadRequestException("Channel connection is not a Gmail account");
    }
    if (connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
      throw new BadRequestException("Gmail connection is not active");
    }
    if (connection.getExternalAccountId() == null || connection.getExternalAccountId().isBlank()) {
      throw new BadRequestException("Gmail sender address is missing");
    }

    List<String> to = normalizeRecipients(request.to());
    List<String> cc = normalizeRecipients(request.cc());
    List<String> bcc = normalizeRecipients(request.bcc());
    if (to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
      throw new BadRequestException("At least one email recipient is required");
    }

    String gmailAccount = connection.getExternalAccountId();
    GmailQuotaReservation sendReservation =
        quotaManager.reserve(
            gmailAccount,
            GmailQuotaOperation.SEND_MESSAGE,
            1,
            GmailQuotaTrafficClass.INTERACTIVE);
    if (!sendReservation.allowed()) {
      throw new ServiceUnavailableException(
          "Gmail is temporarily busy. Please try sending again shortly.");
    }

    String accessToken = tokenService.accessToken(connection);
    String rawMessage = rawMessage(gmailAccount, request, to, cc, bcc);
    String threadId = trimToNull(request.threadId());

    SendMessageResponse sent;
    try {
      sent = gmailApiClient.sendMessage(accessToken, rawMessage, threadId);
    } catch (GmailRateLimitException exception) {
      quotaManager.penalize(gmailAccount, 1, exception.retryAfter());
      throw new ServiceUnavailableException(
          "Gmail is temporarily busy. Please try sending again shortly.");
    }
    Instant sentAt = Instant.now();

    try {
      GmailQuotaReservation refreshReservation =
          quotaManager.reserve(
              gmailAccount,
              GmailQuotaOperation.GET_MESSAGE,
              1,
              GmailQuotaTrafficClass.INTERACTIVE);
      if (refreshReservation.allowed()) {
        var storedMessage = gmailApiClient.message(accessToken, sent.id());
        if (storedMessage != null) {
          persistenceService.persistBatch(connection, List.of(storedMessage));
        }
      }
    } catch (GmailRateLimitException exception) {
      quotaManager.penalize(gmailAccount, 1, exception.retryAfter());
      log.info(
          "Gmail message sent; local refresh deferred by provider backpressure connectionId={} gmailMessageId={}",
          connectionId,
          sent.id());
    } catch (RuntimeException exception) {
      log.warn(
          "Gmail message sent but local inbox refresh failed connectionId={} gmailMessageId={} reason={}",
          connectionId,
          sent.id(),
          exception.getClass().getSimpleName());
    }

    return new GmailSendResponse(
        connection.getId().toString(),
        sent.id(),
        sent.threadId(),
        connection.getExternalAccountId(),
        to,
        cc,
        bcc,
        trimToNull(request.subject()),
        sentAt);
  }

  private String rawMessage(
      String from,
      GmailSendRequest request,
      List<String> to,
      List<String> cc,
      List<String> bcc) {
    try {
      Session session = Session.getInstance(new Properties());
      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(from));

      if (!to.isEmpty()) {
        message.setRecipients(Message.RecipientType.TO, recipients(to));
      }
      if (!cc.isEmpty()) {
        message.setRecipients(Message.RecipientType.CC, recipients(cc));
      }
      if (!bcc.isEmpty()) {
        message.setRecipients(Message.RecipientType.BCC, recipients(bcc));
      }

      message.setSubject(
          request.subject() == null ? "" : request.subject().trim(), StandardCharsets.UTF_8.name());
      message.setText(request.bodyText(), StandardCharsets.UTF_8.name());

      String inReplyTo = trimToNull(request.inReplyTo());
      if (inReplyTo != null) {
        message.setHeader("In-Reply-To", inReplyTo);
      }
      String references = trimToNull(request.references());
      if (references != null) {
        message.setHeader("References", references);
      }

      message.setSentDate(new Date());
      message.saveChanges();

      ByteArrayOutputStream output = new ByteArrayOutputStream();
      message.writeTo(output);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Unable to create Gmail message");
    }
  }

  private InternetAddress[] recipients(List<String> recipients) {
    List<InternetAddress> addresses = new ArrayList<>(recipients.size());
    for (String recipient : recipients) {
      try {
        InternetAddress address = new InternetAddress(recipient, true);
        address.validate();
        addresses.add(address);
      } catch (Exception exception) {
        throw new BadRequestException("Invalid email recipient: " + recipient);
      }
    }
    return addresses.toArray(InternetAddress[]::new);
  }

  private List<String> normalizeRecipients(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }

    Set<String> unique = new LinkedHashSet<>();
    for (String value : values) {
      String normalized = trimToNull(value);
      if (normalized != null) {
        unique.add(normalized.toLowerCase(Locale.ROOT));
      }
    }
    return List.copyOf(unique);
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}

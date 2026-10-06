package com.messaging.channel.gmail;

import com.messaging.channel.email.EmailAttachment;
import com.messaging.channel.email.EmailAttachmentDataSource;
import com.messaging.channel.email.EmailAttachmentService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.gmail.GmailApiClient.SendMessageResponse;
import com.messaging.channel.gmail.dto.GmailSendRequest;
import com.messaging.channel.gmail.dto.GmailSendResponse;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.inbox.dto.InboxEmailChangedEvent;
import com.messaging.realtime.service.RealtimeService;
import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

  private final GmailTokenService tokenService;
  private final GmailApiClient gmailApiClient;
  private final GmailInboxPersistenceService persistenceService;
  private final EmailAttachmentService attachmentService;
  private final GmailDistributedQuotaManager quotaManager;
  private final RealtimeService realtimeService;

  public GmailSendResponse send(ChannelConnection connection, GmailSendRequest request) {
    Long connectionId = connection.getId();
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
    String threadId = trimToNull(request.threadId());
    List<EmailAttachment> attachments =
        attachmentService.resolve(connection.getCompanyId(), request.attachmentIds());

    SendMessageResponse sent;
    try {
      if (attachments.isEmpty()) {
        String rawMessage = rawMessage(gmailAccount, request, to, cc, bcc);
        sent = gmailApiClient.sendMessage(accessToken, rawMessage, threadId);
      } else {
        MimeMessage message = mimeMessage(gmailAccount, request, to, cc, bcc, attachments);
        sent =
            gmailApiClient.sendMimeMessage(
                accessToken,
                threadId,
                outputStream -> {
                  try {
                    message.writeTo(outputStream);
                  } catch (MessagingException exception) {
                    throw new IOException("Unable to stream Gmail MIME message", exception);
                  }
                });
      }
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
          var persisted = persistenceService.persistBatch(connection, List.of(storedMessage));
          realtimeService.sendToCompany(
              connection.getCompanyId(),
              GmailRealtimeWorker.INBOX_CHANGED_EVENT,
              InboxEmailChangedEvent.boundedDelta(
                  connection.getId(),
                  ProviderType.GMAIL,
                  persisted.conversationIds().stream().map(Long::valueOf).toList(),
                  persisted.messageIds().stream().map(Long::valueOf).toList(),
                  List.of()));
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

  private MimeMessage mimeMessage(
      String from,
      GmailSendRequest request,
      List<String> to,
      List<String> cc,
      List<String> bcc,
      List<EmailAttachment> attachments) {
    try {
      Session session = Session.getInstance(new Properties());
      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(from));
      if (!to.isEmpty()) message.setRecipients(Message.RecipientType.TO, recipients(to));
      if (!cc.isEmpty()) message.setRecipients(Message.RecipientType.CC, recipients(cc));
      if (!bcc.isEmpty()) message.setRecipients(Message.RecipientType.BCC, recipients(bcc));

      message.setSubject(
          request.subject() == null ? "" : request.subject().trim(), StandardCharsets.UTF_8.name());

      MimeMultipart multipart = new MimeMultipart("mixed");
      MimeBodyPart bodyPart = new MimeBodyPart();
      bodyPart.setText(request.bodyText(), StandardCharsets.UTF_8.name());
      multipart.addBodyPart(bodyPart);

      for (EmailAttachment attachment : attachments) {
        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDataHandler(new DataHandler(new EmailAttachmentDataSource(attachment)));
        attachmentPart.setFileName(attachment.fileName());
        attachmentPart.setDisposition(Part.ATTACHMENT);
        multipart.addBodyPart(attachmentPart);
      }
      message.setContent(multipart);

      String inReplyTo = trimToNull(request.inReplyTo());
      if (inReplyTo != null) message.setHeader("In-Reply-To", inReplyTo);
      String references = trimToNull(request.references());
      if (references != null) message.setHeader("References", references);

      message.setSentDate(new Date());
      message.saveChanges();
      return message;
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Unable to create Gmail message");
    }
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

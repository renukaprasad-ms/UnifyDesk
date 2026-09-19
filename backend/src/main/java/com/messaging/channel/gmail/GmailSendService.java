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
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailSendService {

  private final ChannelConnectionRepository connectionRepository;
  private final GmailTokenService tokenService;
  private final GmailApiClient gmailApiClient;

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

    String accessToken = tokenService.accessToken(connection);
    String rawMessage = rawMessage(connection.getExternalAccountId(), request);
    SendMessageResponse sent = gmailApiClient.sendMessage(accessToken, rawMessage);
    Instant sentAt = Instant.now();

    return new GmailSendResponse(
        connection.getId().toString(),
        sent.id(),
        sent.threadId(),
        connection.getExternalAccountId(),
        request.to().trim(),
        request.subject() == null ? null : request.subject().trim(),
        sentAt);
  }

  private String rawMessage(String from, GmailSendRequest request) {
    try {
      Session session = Session.getInstance(new Properties());
      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(from));
      message.setRecipient(Message.RecipientType.TO, new InternetAddress(request.to().trim()));
      message.setSubject(
          request.subject() == null ? "" : request.subject().trim(), StandardCharsets.UTF_8.name());
      message.setText(request.bodyText(), StandardCharsets.UTF_8.name());
      message.setSentDate(new Date());
      message.saveChanges();

      ByteArrayOutputStream output = new ByteArrayOutputStream();
      message.writeTo(output);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
    } catch (Exception exception) {
      throw new BadRequestException("Unable to create Gmail message");
    }
  }
}

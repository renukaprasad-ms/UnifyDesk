package com.messaging.inbox.service;

import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.gmail.GmailApiClient;
import com.messaging.channel.gmail.GmailTokenService;
import com.messaging.channel.microsoft.MicrosoftApiClient;
import com.messaging.channel.microsoft.MicrosoftTokenService;
import com.messaging.channel.zoho.ZohoMailApiClient;
import com.messaging.channel.zoho.ZohoMailStoredToken;
import com.messaging.channel.zoho.ZohoMailTokenService;
import com.messaging.common.exception.NotFoundException;
import com.messaging.inbox.entity.InboxMessageAttachment;
import com.messaging.inbox.repository.InboxMessageAttachmentRepository;
import com.messaging.media.service.MediaService;
import java.io.IOException;
import java.io.OutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboxAttachmentStreamingService {

  private final InboxMessageAttachmentRepository attachmentRepository;
  private final GmailApiClient gmailApiClient;
  private final GmailTokenService gmailTokenService;
  private final MicrosoftApiClient microsoftApiClient;
  private final MicrosoftTokenService microsoftTokenService;
  private final ZohoMailApiClient zohoMailApiClient;
  private final ZohoMailTokenService zohoMailTokenService;
  private final MediaService mediaService;

  @Transactional(readOnly = true)
  public AttachmentDescriptor descriptor(Long companyId, Long attachmentId) {
    InboxMessageAttachment attachment = requireAttachment(companyId, attachmentId);
    return new AttachmentDescriptor(
        attachment.getId(),
        attachment.getFilename(),
        attachment.getMimeType(),
        attachment.getSizeBytes());
  }

  @Transactional(readOnly = true)
  public void stream(Long companyId, Long attachmentId, OutputStream outputStream) {
    InboxMessageAttachment attachment = requireAttachment(companyId, attachmentId);
    var message = attachment.getMessage();
    String externalAttachmentId = attachment.getExternalAttachmentId();
    if (externalAttachmentId != null && externalAttachmentId.startsWith("media:")) {
      long mediaId = parseMediaId(externalAttachmentId);
      try (var input = mediaService.openTrustedObject(mediaId, companyId)) {
        input.transferTo(outputStream);
        outputStream.flush();
        return;
      } catch (IOException exception) {
        throw new NotFoundException("Inbox attachment is not available");
      }
    }

    var connection = message.getConnection();
    ProviderType providerType = connection.getProviderType();

    switch (providerType) {
      case GMAIL ->
          gmailApiClient.streamAttachment(
              gmailTokenService.accessToken(connection),
              message.getExternalMessageId(),
              attachment.getExternalAttachmentId(),
              outputStream);
      case MICROSOFT ->
          microsoftApiClient.streamAttachment(
              microsoftTokenService.accessToken(connection),
              message.getExternalMessageId(),
              attachment.getExternalAttachmentId(),
              outputStream);
      case ZOHO_MAIL -> {
        String[] ref = zohoAttachmentRef(attachment.getExternalAttachmentId());
        ZohoMailStoredToken token = zohoMailTokenService.tokenSet(connection);
        zohoMailApiClient.streamAttachment(
            token.mailApiBaseUrl(),
            token.accessToken(),
            token.accountId(),
            ref[0],
            message.getExternalMessageId(),
            ref[1],
            outputStream);
      }
      default -> throw new NotFoundException("Inbox attachment is not available for this provider");
    }
  }

  private String[] zohoAttachmentRef(String externalAttachmentId) {
    if (externalAttachmentId == null || !externalAttachmentId.startsWith("zoho:")) {
      throw new NotFoundException("Inbox attachment is not available");
    }
    String[] parts = externalAttachmentId.split(":", 3);
    if (parts.length != 3 || parts[1].isBlank() || parts[2].isBlank()) {
      throw new NotFoundException("Inbox attachment is not available");
    }
    return new String[] {parts[1], parts[2]};
  }

  private long parseMediaId(String externalAttachmentId) {
    try {
      return Long.parseLong(externalAttachmentId.substring("media:".length()));
    } catch (RuntimeException exception) {
      throw new NotFoundException("Inbox attachment is not available");
    }
  }

  private InboxMessageAttachment requireAttachment(Long companyId, Long attachmentId) {
    return attachmentRepository
        .findByIdAndMessageConnectionCompanyId(attachmentId, companyId)
        .orElseThrow(() -> new NotFoundException("Inbox attachment not found"));
  }

  public record AttachmentDescriptor(
      Long id, String filename, String mimeType, Long sizeBytes) {}
}

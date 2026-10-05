package com.messaging.inbox.service;

import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.gmail.GmailApiClient;
import com.messaging.channel.gmail.GmailTokenService;
import com.messaging.channel.microsoft.MicrosoftApiClient;
import com.messaging.channel.microsoft.MicrosoftTokenService;
import com.messaging.common.exception.NotFoundException;
import com.messaging.inbox.entity.InboxMessageAttachment;
import com.messaging.inbox.repository.InboxMessageAttachmentRepository;
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
      default -> throw new NotFoundException("Inbox attachment is not available for this provider");
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

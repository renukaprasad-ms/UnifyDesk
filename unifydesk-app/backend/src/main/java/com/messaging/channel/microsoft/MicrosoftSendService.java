package com.messaging.channel.microsoft;

import com.messaging.channel.email.EmailAttachment;
import com.messaging.channel.email.EmailAttachmentService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.microsoft.MicrosoftApiClient.*;
import com.messaging.channel.microsoft.dto.MicrosoftSendRequest;
import com.messaging.channel.microsoft.dto.MicrosoftSendResponse;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ServiceUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MicrosoftSendService {

  private final MicrosoftTokenService tokenService;
  private final MicrosoftApiClient apiClient;
  private final MicrosoftSyncStateService syncStateService;
  private final EmailAttachmentService attachmentService;

  public MicrosoftSendResponse send(
      ChannelConnection connection, MicrosoftSendRequest request) {
    Long connectionId = connection.getId();

    List<String> to = normalize(request.to());
    List<String> cc = normalize(request.cc());
    List<String> bcc = normalize(request.bcc());
    boolean reply = request.replyToMessageId() != null && !request.replyToMessageId().isBlank();
    if (!reply && to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
      throw new BadRequestException("At least one email recipient is required");
    }

    String accessToken = tokenService.accessToken(connection);
    List<EmailAttachment> attachments =
        attachmentService.resolve(connection.getCompanyId(), request.attachmentIds());

    if (attachments.isEmpty()) {
      if (reply) {
        apiClient.reply(
            accessToken,
            request.replyToMessageId().trim(),
            request.bodyText(),
            Boolean.TRUE.equals(request.replyAll()));
      } else {
        GraphSendMessage message =
            new GraphSendMessage(
                trimToEmpty(request.subject()),
                new GraphBody("Text", request.bodyText()),
                recipients(to),
                recipients(cc),
                recipients(bcc));
        apiClient.sendMail(accessToken, new SendMailRequest(message, true));
      }
    } else {
      sendWithAttachments(accessToken, request, to, cc, bcc, attachments, reply);
    }

    // Graph send/reply is asynchronous (202). Delta sync reconciles provider-assigned IDs
    // from Sent Items without fabricating local identifiers.
    syncStateService.triggerConnection(connectionId);

    return new MicrosoftSendResponse(
        connectionId.toString(),
        connection.getExternalAccountId(),
        to,
        cc,
        bcc,
        trimToEmpty(request.subject()),
        Instant.now());
  }

  private void sendWithAttachments(
      String accessToken,
      MicrosoftSendRequest request,
      List<String> to,
      List<String> cc,
      List<String> bcc,
      List<EmailAttachment> attachments,
      boolean reply) {
    GraphMessage draft =
        reply
            ? apiClient.createReplyDraft(
                accessToken,
                request.replyToMessageId().trim(),
                Boolean.TRUE.equals(request.replyAll()))
            : apiClient.createDraft(
                accessToken,
                new GraphSendMessage(
                    trimToEmpty(request.subject()),
                    new GraphBody("Text", request.bodyText()),
                    recipients(to),
                    recipients(cc),
                    recipients(bcc)));

    if (draft == null || draft.id() == null || draft.id().isBlank()) {
      throw new ServiceUnavailableException("Microsoft did not create an email draft");
    }

    String draftId = draft.id();
    try {
      if (reply) {
        apiClient.updateDraftBody(accessToken, draftId, request.bodyText());
      }
      for (EmailAttachment attachment : attachments) {
        addAttachment(accessToken, draftId, attachment);
      }
      apiClient.sendDraft(accessToken, draftId);
    } catch (RuntimeException exception) {
      apiClient.deleteDraftBestEffort(accessToken, draftId);
      throw exception;
    }
  }

  private void addAttachment(
      String accessToken, String draftId, EmailAttachment attachment) {
    final long smallAttachmentLimit = 3L * 1024L * 1024L;
    if (attachment.sizeBytes() <= smallAttachmentLimit) {
      try (InputStream input = attachment.openStream()) {
        byte[] content = input.readNBytes((int) attachment.sizeBytes() + 1);
        if (content.length != attachment.sizeBytes()) {
          throw new ServiceUnavailableException("Stored email attachment size changed");
        }
        apiClient.addSmallFileAttachment(
            accessToken,
            draftId,
            attachment.fileName(),
            attachment.contentType(),
            content);
        return;
      } catch (IOException exception) {
        throw new ServiceUnavailableException("Unable to read email attachment", exception);
      }
    }

    AttachmentUploadSession session =
        apiClient.createAttachmentUploadSession(
            accessToken,
            draftId,
            attachment.fileName(),
            attachment.contentType(),
            attachment.sizeBytes());
    if (session == null || session.uploadUrl() == null || session.uploadUrl().isBlank()) {
      throw new ServiceUnavailableException("Microsoft did not create an attachment upload session");
    }

    final int chunkSize = 320 * 1024;
    long offset = 0;
    try (InputStream input = attachment.openStream()) {
      while (offset < attachment.sizeBytes()) {
        int requested = (int) Math.min(chunkSize, attachment.sizeBytes() - offset);
        byte[] chunk = input.readNBytes(requested);
        if (chunk.length == 0) {
          throw new ServiceUnavailableException("Stored email attachment ended unexpectedly");
        }
        apiClient.uploadAttachmentChunk(
            session.uploadUrl(), chunk, offset, attachment.sizeBytes());
        offset += chunk.length;
      }
      if (input.read() != -1) {
        throw new ServiceUnavailableException("Stored email attachment size changed");
      }
    } catch (IOException exception) {
      throw new ServiceUnavailableException("Unable to stream email attachment", exception);
    }
  }

  private List<GraphRecipient> recipients(List<String> values) {
    return values.stream()
        .map(value -> new GraphRecipient(new GraphEmailAddress(null, value)))
        .toList();
  }

  private List<String> normalize(List<String> values) {
    if (values == null) return List.of();
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        unique.add(value.trim().toLowerCase(Locale.ROOT));
      }
    }
    return List.copyOf(unique);
  }

  private String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }
}

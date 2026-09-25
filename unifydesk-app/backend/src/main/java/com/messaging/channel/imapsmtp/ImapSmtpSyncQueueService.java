package com.messaging.channel.imapsmtp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.email.EmailSyncRequest;
import com.messaging.channel.email.EmailSyncRequestValidator;
import com.messaging.channel.imapsmtp.dto.ImapSmtpSyncQueuedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImapSmtpSyncQueueService {

  private final ImapSmtpProperties properties;
  private final ImapSmtpConnectionService connectionService;
  private final AsyncJobService asyncJobService;
  private final ObjectMapper objectMapper;

  public ImapSmtpSyncQueuedResponse enqueue(
      Long companyId, Long userId, Long connectionId, EmailSyncRequest request) {
    connectionService.requireActive(companyId, connectionId);
    EmailSyncRequestValidator.validate(
        request, properties.getSyncMaxRangeDays(), properties.getSyncMaxMessages());

    var existing =
        asyncJobService.findActive(
            companyId, AsyncJobType.IMAP_SMTP_SYNC, connectionId.toString());
    if (existing.isPresent()) {
      AsyncJobResponse job = existing.get();
      return response(connectionId, job, request);
    }

    String metadata = metadata(connectionId, request);
    AsyncJobResponse job =
        asyncJobService.create(
            companyId,
            userId,
            AsyncJobType.IMAP_SMTP_SYNC,
            connectionId.toString(),
            metadata,
            "IMAP email sync queued");
    return response(connectionId, job, request);
  }

  public AsyncJobResponse active(Long companyId, Long connectionId) {
    connectionService.requireActive(companyId, connectionId);
    return asyncJobService
        .findActive(companyId, AsyncJobType.IMAP_SMTP_SYNC, connectionId.toString())
        .orElse(null);
  }

  private String metadata(Long connectionId, EmailSyncRequest request) {
    try {
      return objectMapper.writeValueAsString(new SyncMetadata(connectionId, request));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize IMAP sync request", exception);
    }
  }

  private ImapSmtpSyncQueuedResponse response(
      Long connectionId, AsyncJobResponse job, EmailSyncRequest request) {
    return new ImapSmtpSyncQueuedResponse(
        connectionId.toString(),
        job.jobId(),
        job.status().name(),
        request.mode(),
        request.folder(),
        request.fromDate(),
        request.toDate(),
        request.messageCount());
  }

  public record SyncMetadata(Long connectionId, EmailSyncRequest request) {}
}

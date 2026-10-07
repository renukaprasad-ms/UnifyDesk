package com.messaging.channel.zoho;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.email.EmailSyncQueuedResult;
import com.messaging.channel.email.EmailSyncRequest;
import com.messaging.channel.email.EmailSyncRequestValidator;
import com.messaging.channel.entity.ChannelConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ZohoMailSyncQueueService {

  private final ZohoMailOAuthProperties properties;
  private final AsyncJobService asyncJobService;
  private final ObjectMapper objectMapper;

  public EmailSyncQueuedResult enqueue(
      ChannelConnection connection, Long userId, EmailSyncRequest request) {
    EmailSyncRequestValidator.validate(
        request, properties.getSyncMaxRangeDays(), properties.getSyncMaxMessages());

    Long companyId = connection.getCompanyId();
    Long connectionId = connection.getId();
    var existing =
        asyncJobService.findActive(
            companyId, AsyncJobType.ZOHO_MAIL_SYNC, connectionId.toString());
    if (existing.isPresent()) {
      return response(connectionId, existing.get(), request);
    }

    AsyncJobResponse job =
        asyncJobService.create(
            companyId,
            userId,
            AsyncJobType.ZOHO_MAIL_SYNC,
            connectionId.toString(),
            metadata(connectionId, request),
            "Zoho Mail sync queued");
    return response(connectionId, job, request);
  }

  public AsyncJobResponse active(ChannelConnection connection) {
    return asyncJobService
        .findActive(
            connection.getCompanyId(),
            AsyncJobType.ZOHO_MAIL_SYNC,
            connection.getId().toString())
        .orElse(null);
  }

  private String metadata(Long connectionId, EmailSyncRequest request) {
    try {
      return objectMapper.writeValueAsString(new SyncMetadata(connectionId, request));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Zoho Mail sync request", exception);
    }
  }

  private EmailSyncQueuedResult response(
      Long connectionId, AsyncJobResponse job, EmailSyncRequest request) {
    return new EmailSyncQueuedResult(
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

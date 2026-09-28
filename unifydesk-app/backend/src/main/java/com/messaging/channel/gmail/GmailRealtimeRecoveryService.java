package com.messaging.channel.gmail;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.gmail.GmailApiClient.BatchMessageResult;
import com.messaging.channel.gmail.GmailApiClient.GmailMessage;
import com.messaging.channel.gmail.GmailApiClient.MessageListResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailRealtimeRecoveryService {

  private final GmailOAuthProperties properties;
  private final GmailApiClient apiClient;
  private final GmailDistributedQuotaManager quotaManager;
  private final GmailInboxPersistenceService persistenceService;

  public int recover(ChannelConnection connection, String accessToken) {
    int maxMessages = Math.max(1, properties.getRealtimeRecoveryMaxMessages());
    Instant recoveryFrom =
        LocalDate.now(ZoneOffset.UTC)
            .minusDays(Math.max(1, properties.getRealtimeRecoveryDays()))
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC);
    long afterEpoch = recoveryFrom.getEpochSecond() - 1;
    String query = "{in:inbox in:sent} after:" + afterEpoch;

    String pageToken = null;
    int processed = 0;
    Set<String> seenMessageIds = new LinkedHashSet<>();
    boolean reachedEnd = false;

    do {
      reserve(
          connection,
          GmailQuotaOperation.LIST_MESSAGES,
          1);

      int remaining = maxMessages - processed;
      MessageListResponse page =
          apiClient.listMessages(accessToken, Math.min(500, remaining), query, pageToken);

      List<String> ids =
          page.messages() == null
              ? List.of()
              : page.messages().stream()
                  .map(GmailApiClient.MessageReference::id)
                  .filter(id -> id != null && !id.isBlank())
                  .limit(remaining)
                  .toList();
      seenMessageIds.addAll(ids);

      for (int offset = 0; offset < ids.size(); offset += Math.max(1, properties.getRealtimeBatchSize())) {
        int end = Math.min(ids.size(), offset + Math.max(1, properties.getRealtimeBatchSize()));
        List<String> batchIds = ids.subList(offset, end);

        reserve(
            connection,
            GmailQuotaOperation.GET_MESSAGE,
            batchIds.size());

        List<BatchMessageResult> results = apiClient.batchMessages(accessToken, batchIds);
        List<GmailMessage> successful = new ArrayList<>(results.size());
        Set<String> deleted = new LinkedHashSet<>();

        for (BatchMessageResult result : results) {
          if (result.successful()) {
            successful.add(result.message());
          } else if (result.status() == 404) {
            deleted.add(result.messageId());
          } else if (result.retryable()) {
            throw new GmailRateLimitException(result.reason(), result.retryAfter());
          }
        }

        if (!successful.isEmpty()) {
          persistenceService.persistBatch(connection, successful, null, false);
        }
        if (!deleted.isEmpty()) {
          persistenceService.markDeleted(connection, deleted);
        }
        processed += batchIds.size();
      }

      pageToken = page.nextPageToken();
      reachedEnd = pageToken == null || pageToken.isBlank();
    } while (!reachedEnd && processed < maxMessages);

    if (reachedEnd) {
      Set<String> localActive =
          persistenceService.activeFolderMessageIdsSince(connection, recoveryFrom);
      localActive.removeAll(seenMessageIds);
      if (!localActive.isEmpty()) {
        persistenceService.markDeleted(connection, localActive);
      }
    }

    return processed;
  }

  private void reserve(
      ChannelConnection connection, GmailQuotaOperation operation, int count) {
    GmailQuotaReservation reservation =
        quotaManager.reserve(
            connection.getExternalAccountId(),
            operation,
            count,
            GmailQuotaTrafficClass.BACKGROUND);
    if (!reservation.allowed()) {
      throw new GmailRealtimeDeferredException(
          "Gmail recovery is waiting for quota", reservation.availableAt());
    }
  }
}

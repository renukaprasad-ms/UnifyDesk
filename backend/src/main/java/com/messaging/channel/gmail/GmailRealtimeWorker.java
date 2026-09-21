package com.messaging.channel.gmail;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.gmail.GmailApiClient.BatchMessageResult;
import com.messaging.channel.gmail.GmailApiClient.GmailMessage;
import com.messaging.channel.gmail.GmailApiClient.HistoryLabelChange;
import com.messaging.channel.gmail.GmailApiClient.HistoryListResponse;
import com.messaging.channel.gmail.GmailApiClient.HistoryMessageChange;
import com.messaging.channel.gmail.GmailApiClient.HistoryRecord;
import com.messaging.channel.gmail.GmailRealtimeStateService.ClaimedRealtimeSync;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.inbox.dto.InboxEmailChangedEvent;
import com.messaging.realtime.service.RealtimeService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmailRealtimeWorker {

  public static final String INBOX_CHANGED_EVENT = "inbox.email.changed";

  private final GmailOAuthProperties properties;
  private final GmailRealtimeStateService stateService;
  private final GmailTokenService tokenService;
  private final GmailApiClient apiClient;
  private final GmailDistributedQuotaManager quotaManager;
  private final GmailInboxPersistenceService persistenceService;
  private final GmailRealtimeRecoveryService recoveryService;
  private final GmailWatchService watchService;
  private final ChannelConnectionRepository connectionRepository;
  private final CompanyRepository companyRepository;
  private final RealtimeService realtimeService;

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicInteger inFlight = new AtomicInteger();

  @Scheduled(fixedDelayString = "${app.channel.gmail.realtime-worker-poll-interval-ms:500}")
  public void dispatch() {
    if (!properties.realtimeConfigured()) return;

    stateService.recoverStaleClaims();

    int available = Math.max(1, properties.getRealtimeWorkerConcurrency()) - inFlight.get();
    if (available <= 0) return;

    List<ClaimedRealtimeSync> claimed = stateService.claim(available);
    for (ClaimedRealtimeSync sync : claimed) {
      inFlight.incrementAndGet();
      executor.submit(
          () -> {
            try {
              process(sync);
            } finally {
              inFlight.decrementAndGet();
            }
          });
    }
  }

  void process(ClaimedRealtimeSync sync) {
    int attempt = sync.attempt();
    try {
      ChannelConnection connection =
          connectionRepository
              .findByIdAndCompanyId(sync.connectionId(), sync.companyId())
              .orElse(null);
      if (connection == null
          || connection.getProviderType() != ProviderType.GMAIL
          || connection.getStatus() != ChannelConnectionStatus.ACTIVE
          || companyRepository
              .findById(sync.companyId())
              .map(company -> company.getStatus() != CompanyStatus.ACTIVE)
              .orElse(true)) {
        stateService.complete(sync.connectionId(), sync.targetHistoryId());
        return;
      }

      if (!hasText(sync.cursorHistoryId())) {
        stateService.complete(sync.connectionId(), sync.targetHistoryId());
        watchService.ensureWatch(connection, false);
        return;
      }

      if (GmailRealtimeStateService.compareHistoryId(
              sync.targetHistoryId(), sync.cursorHistoryId())
          <= 0) {
        stateService.complete(sync.connectionId(), sync.cursorHistoryId());
        return;
      }

      String accessToken = tokenService.accessToken(connection);
      String pageToken = null;
      String processedHistoryId = sync.cursorHistoryId();
      int changed = 0;
      int deleted = 0;
      Set<Long> changedConversationIds = new LinkedHashSet<>();
      Set<Long> changedMessageIds = new LinkedHashSet<>();
      Set<Long> deletedMessageIds = new LinkedHashSet<>();
      boolean providerCalled = false;

      do {
        GmailQuotaReservation historyReservation =
            quotaManager.reserve(
                connection.getExternalAccountId(),
                GmailQuotaOperation.HISTORY_LIST,
                1,
                GmailQuotaTrafficClass.BACKGROUND);
        if (!historyReservation.allowed()) {
          stateService.defer(
              sync.connectionId(),
              historyReservation.availableAt(),
              "Waiting for Gmail history quota");
          return;
        }

        if (!providerCalled) {
          attempt = stateService.startAttempt(sync.connectionId());
          providerCalled = true;
        }

        HistoryListResponse response =
            apiClient.history(
                accessToken,
                sync.cursorHistoryId(),
                pageToken,
                properties.getRealtimeHistoryPageSize());

        Set<String> affectedMessageIds = affectedMessageIds(response.history());
        RefreshResult refreshResult =
            refreshMessages(connection, accessToken, affectedMessageIds);
        changed += refreshResult.changed();
        deleted += refreshResult.deleted();
        changedConversationIds.addAll(refreshResult.conversationIds());
        changedMessageIds.addAll(refreshResult.messageIds());
        deletedMessageIds.addAll(refreshResult.deletedMessageIds());

        if (hasText(response.historyId())
            && GmailRealtimeStateService.compareHistoryId(
                    response.historyId(), processedHistoryId)
                > 0) {
          processedHistoryId = response.historyId();
        }
        pageToken = response.nextPageToken();
      } while (hasText(pageToken));

      if (GmailRealtimeStateService.compareHistoryId(
              sync.targetHistoryId(), processedHistoryId)
          > 0) {
        processedHistoryId = sync.targetHistoryId();
      }

      stateService.complete(sync.connectionId(), processedHistoryId);
      connection.setLastSyncAt(Instant.now());
      connectionRepository.save(connection);

      if (changed > 0 || deleted > 0) {
        realtimeService.sendToCompany(
            sync.companyId(),
            INBOX_CHANGED_EVENT,
            InboxEmailChangedEvent.boundedDelta(
                sync.connectionId(),
                ProviderType.GMAIL,
                changedConversationIds,
                changedMessageIds,
                deletedMessageIds));
      }

      log.info(
          "gmail_realtime_sync_completed connectionId={} account={} changed={} deleted={} historyId={}",
          sync.connectionId(),
          sync.gmailAccount(),
          changed,
          deleted,
          processedHistoryId);
    } catch (GmailRealtimeDeferredException exception) {
      stateService.defer(sync.connectionId(), exception.availableAt(), exception.getMessage());
    } catch (GmailHistoryExpiredException exception) {
      recoverExpiredHistory(sync);
    } catch (GmailRateLimitException exception) {
      Instant retryAt =
          quotaManager.penalize(
              sync.gmailAccount(), Math.max(1, attempt), exception.retryAfter());
      stateService.failOrRetry(
          sync.connectionId(),
          Math.max(1, attempt),
          exception.reason(),
          retryAt);
    } catch (RuntimeException exception) {
      int resolvedAttempt = Math.max(1, attempt);
      Instant retryAt = Instant.now().plus(retryDelay(resolvedAttempt));
      stateService.failOrRetry(
          sync.connectionId(),
          resolvedAttempt,
          safeMessage(exception),
          retryAt);
      log.warn(
          "gmail_realtime_sync_retry connectionId={} account={} attempt={} reason={}",
          sync.connectionId(),
          sync.gmailAccount(),
          resolvedAttempt,
          exception.getClass().getSimpleName(),
          exception);
    }
  }

  private void recoverExpiredHistory(ClaimedRealtimeSync sync) {
    try {
      ChannelConnection connection =
          connectionRepository
              .findByIdAndCompanyId(sync.connectionId(), sync.companyId())
              .orElseThrow(() -> new IllegalStateException("Gmail connection not found"));

      String accessToken = tokenService.accessToken(connection);
      int recovered = recoveryService.recover(connection, accessToken);

      GmailQuotaReservation profileReservation =
          quotaManager.reserve(
              connection.getExternalAccountId(),
              GmailQuotaOperation.GET_PROFILE,
              1,
              GmailQuotaTrafficClass.BACKGROUND);
      if (!profileReservation.allowed()) {
        stateService.defer(
            sync.connectionId(),
            profileReservation.availableAt(),
            "Waiting to reset expired Gmail history cursor");
        return;
      }

      String currentHistoryId = apiClient.profile(accessToken).historyId();
      stateService.resetExpiredCursor(
          sync.connectionId(),
          currentHistoryId,
          "Recovered after Gmail history cursor expired");
      watchService.ensureWatch(connection, true);

      if (recovered > 0) {
        realtimeService.sendToCompany(
            sync.companyId(),
            INBOX_CHANGED_EVENT,
            InboxEmailChangedEvent.fullRefresh(sync.connectionId(), ProviderType.GMAIL));
      }

      log.warn(
          "gmail_realtime_history_recovered connectionId={} account={} recovered={} newHistoryId={}",
          sync.connectionId(),
          sync.gmailAccount(),
          recovered,
          currentHistoryId);
    } catch (GmailRealtimeDeferredException exception) {
      stateService.defer(sync.connectionId(), exception.availableAt(), exception.getMessage());
    } catch (RuntimeException exception) {
      int attempt = Math.max(1, sync.attempt() + 1);
      stateService.failOrRetry(
          sync.connectionId(),
          attempt,
          "Gmail stale-history recovery failed: " + safeMessage(exception),
          Instant.now().plus(retryDelay(attempt)));
    }
  }

  private RefreshResult refreshMessages(
      ChannelConnection connection, String accessToken, Set<String> messageIds) {
    if (messageIds == null || messageIds.isEmpty()) {
      return new RefreshResult(0, 0);
    }

    List<String> ids = new ArrayList<>(messageIds);
    int changed = 0;
    int deletedCount = 0;
    Set<Long> conversationIds = new LinkedHashSet<>();
    Set<Long> changedMessageIds = new LinkedHashSet<>();
    Set<Long> deletedMessageIds = new LinkedHashSet<>();
    int batchSize =
        Math.max(
            1,
            Math.min(properties.getRealtimeBatchSize(), GmailApiClient.MAX_BATCH_SIZE));

    for (int offset = 0; offset < ids.size(); offset += batchSize) {
      int end = Math.min(ids.size(), offset + batchSize);
      List<String> batchIds = ids.subList(offset, end);

      GmailQuotaReservation reservation =
          quotaManager.reserve(
              connection.getExternalAccountId(),
              GmailQuotaOperation.GET_MESSAGE,
              batchIds.size(),
              GmailQuotaTrafficClass.BACKGROUND);
      if (!reservation.allowed()) {
        throw new GmailRealtimeDeferredException(
            "Waiting for Gmail message quota", reservation.availableAt());
      }

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
        var persisted = persistenceService.persistBatch(connection, successful);
        changed += successful.size();
        conversationIds.addAll(longIds(persisted.conversationIds()));
        changedMessageIds.addAll(longIds(persisted.messageIds()));
      }
      if (!deleted.isEmpty()) {
        var deletedResult = persistenceService.markDeleted(connection, deleted);
        deletedCount += deletedResult.deletedCount();
        conversationIds.addAll(deletedResult.conversationIds());
        deletedMessageIds.addAll(deletedResult.messageIds());
      }
    }

    return new RefreshResult(changed, deletedCount, conversationIds, changedMessageIds, deletedMessageIds);
  }

  private Set<String> affectedMessageIds(List<HistoryRecord> history) {
    Set<String> affected = new LinkedHashSet<>();
    if (history == null) return affected;

    for (HistoryRecord record : history) {
      addMessageChanges(affected, record.messagesAdded());
      addMessageChanges(affected, record.messagesDeleted());
      addLabelChanges(affected, record.labelsAdded());
      addLabelChanges(affected, record.labelsRemoved());
    }
    return affected;
  }

  private void addMessageChanges(Set<String> output, List<HistoryMessageChange> changes) {
    if (changes == null) return;
    for (HistoryMessageChange change : changes) {
      if (change != null && change.message() != null && hasText(change.message().id())) {
        output.add(change.message().id());
      }
    }
  }

  private void addLabelChanges(Set<String> output, List<HistoryLabelChange> changes) {
    if (changes == null) return;
    for (HistoryLabelChange change : changes) {
      if (change != null && change.message() != null && hasText(change.message().id())) {
        output.add(change.message().id());
      }
    }
  }

  private Duration retryDelay(int attempt) {
    long base = Math.min(300, 1L << Math.min(Math.max(attempt, 1), 8));
    return Duration.ofMillis(
        base * 1000L + ThreadLocalRandom.current().nextLong(0, 1001));
  }

  private String safeMessage(Throwable error) {
    String message = error.getMessage();
    return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }

  private Set<Long> longIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) return Set.of();
    Set<Long> values = new LinkedHashSet<>();
    for (String id : ids) {
      try {
        values.add(Long.valueOf(id));
      } catch (NumberFormatException ignored) {
        // Local ids are generated by the application; skip malformed values defensively.
      }
    }
    return values;
  }

  private record RefreshResult(
      int changed,
      int deleted,
      Set<Long> conversationIds,
      Set<Long> messageIds,
      Set<Long> deletedMessageIds) {}
}

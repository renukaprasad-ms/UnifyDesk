package com.messaging.channel.gmail;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.inbox.entity.ChannelSyncState;
import com.messaging.inbox.repository.ChannelSyncStateRepository;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GmailRealtimeStateService {

  private final ChannelSyncStateRepository repository;
  private final GmailOAuthProperties properties;

  @Transactional
  public ChannelSyncState ensureState(ChannelConnection connection) {
    return repository
        .findForUpdate(connection.getId(), ProviderType.GMAIL)
        .orElseGet(
            () -> {
              ChannelSyncState state = new ChannelSyncState();
              state.setConnection(connection);
              state.setProviderType(ProviderType.GMAIL);
              return repository.saveAndFlush(state);
            });
  }

  @Transactional
  public void recordWatch(
      ChannelConnection connection, String historyId, Long expirationEpochMillis) {
    ChannelSyncState state = ensureState(connection);
    if (!hasText(state.getCursorValue()) && hasText(historyId)) {
      state.setCursorValue(historyId);
    }
    state.setWatchRenewedAt(Instant.now());
    state.setWatchExpirationAt(
        expirationEpochMillis == null ? null : Instant.ofEpochMilli(expirationEpochMillis));
    state.setRealtimeLastError(null);
    repository.save(state);
  }

  @Transactional
  public boolean enqueueNotification(Long connectionId, String historyId) {
    if (!hasText(historyId)) return false;

    ChannelSyncState state =
        repository
            .findForUpdate(connectionId, ProviderType.GMAIL)
            .orElse(null);
    if (state == null) {
      return false;
    }

    state.setLastNotificationAt(Instant.now());
    String currentPending = state.getPendingHistoryId();
    if (!hasText(currentPending) || compareHistoryId(historyId, currentPending) > 0) {
      state.setPendingHistoryId(historyId);
    }
    if (state.getRealtimeNextAttemptAt() == null
        || state.getRealtimeNextAttemptAt().isAfter(Instant.now())) {
      state.setRealtimeNextAttemptAt(Instant.now());
    }
    repository.save(state);
    return true;
  }

  @Transactional
  public List<ClaimedRealtimeSync> claim(int limit) {
    if (limit <= 0) return List.of();

    Instant staleCutoff = Instant.now().minus(properties.getRealtimeStaleAfter());
    List<ChannelSyncState> states =
        repository.findRealtimeClaimableForUpdate(staleCutoff, limit);
    Instant now = Instant.now();

    for (ChannelSyncState state : states) {
      state.setRealtimeClaimedAt(now);
      state.setRealtimeNextAttemptAt(null);
      state.setRealtimeLastError(null);
    }
    repository.saveAll(states);

    return states.stream()
        .map(
            state ->
                new ClaimedRealtimeSync(
                    state.getConnection().getId(),
                    state.getConnection().getCompanyId(),
                    state.getConnection().getExternalAccountId(),
                    state.getCursorValue(),
                    state.getPendingHistoryId(),
                    state.getRealtimeAttemptCount()))
        .toList();
  }

  @Transactional
  public int startAttempt(Long connectionId) {
    ChannelSyncState state =
        repository
            .findForUpdate(connectionId, ProviderType.GMAIL)
            .orElseThrow(() -> new IllegalStateException("Gmail realtime state not found"));
    state.setRealtimeAttemptCount(state.getRealtimeAttemptCount() + 1);
    repository.save(state);
    return state.getRealtimeAttemptCount();
  }

  @Transactional
  public void complete(Long connectionId, String processedHistoryId) {
    ChannelSyncState state =
        repository
            .findForUpdate(connectionId, ProviderType.GMAIL)
            .orElseThrow(() -> new IllegalStateException("Gmail realtime state not found"));

    if (hasText(processedHistoryId)
        && (!hasText(state.getCursorValue())
            || compareHistoryId(processedHistoryId, state.getCursorValue()) > 0)) {
      state.setCursorValue(processedHistoryId);
    }

    if (hasText(state.getPendingHistoryId())
        && hasText(state.getCursorValue())
        && compareHistoryId(state.getPendingHistoryId(), state.getCursorValue()) <= 0) {
      state.setPendingHistoryId(null);
    }

    state.setLastIncrementalSyncAt(Instant.now());
    state.setRealtimeClaimedAt(null);
    state.setRealtimeAttemptCount(0);
    state.setRealtimeLastError(null);
    state.setRealtimeNextAttemptAt(
        state.getPendingHistoryId() == null ? null : Instant.now());
    repository.save(state);
  }

  @Transactional
  public void resetExpiredCursor(Long connectionId, String newHistoryId, String reason) {
    ChannelSyncState state =
        repository
            .findForUpdate(connectionId, ProviderType.GMAIL)
            .orElseThrow(() -> new IllegalStateException("Gmail realtime state not found"));

    state.setCursorValue(newHistoryId);
    state.setPendingHistoryId(null);
    state.setLastIncrementalSyncAt(Instant.now());
    state.setRealtimeClaimedAt(null);
    state.setRealtimeAttemptCount(0);
    state.setRealtimeNextAttemptAt(null);
    state.setRealtimeLastError(truncate(reason));
    repository.save(state);
  }

  @Transactional
  public void defer(Long connectionId, Instant nextAttemptAt, String reason) {
    ChannelSyncState state =
        repository
            .findForUpdate(connectionId, ProviderType.GMAIL)
            .orElseThrow(() -> new IllegalStateException("Gmail realtime state not found"));
    state.setRealtimeClaimedAt(null);
    state.setRealtimeNextAttemptAt(nextAttemptAt);
    state.setRealtimeLastError(truncate(reason));
    repository.save(state);
  }

  @Transactional
  public void failOrRetry(Long connectionId, int attempt, String reason, Instant nextAttemptAt) {
    ChannelSyncState state =
        repository
            .findForUpdate(connectionId, ProviderType.GMAIL)
            .orElseThrow(() -> new IllegalStateException("Gmail realtime state not found"));

    state.setRealtimeClaimedAt(null);
    state.setRealtimeLastError(truncate(reason));
    if (attempt >= Math.max(1, properties.getRealtimeMaxAttempts())) {
      // Keep pending_history_id intact so the safety scheduler can revive this connection later.
      state.setRealtimeNextAttemptAt(
          Instant.now().plus(properties.getRealtimeSafetySyncInterval()));
      state.setRealtimeAttemptCount(0);
    } else {
      state.setRealtimeNextAttemptAt(nextAttemptAt);
    }
    repository.save(state);
  }

  @Transactional
  public int recoverStaleClaims() {
    return repository.recoverStaleRealtimeClaims(
        Instant.now().minus(properties.getRealtimeStaleAfter()));
  }

  @Transactional(readOnly = true)
  public ChannelSyncState state(Long connectionId) {
    return repository
        .findByConnectionIdAndProviderType(connectionId, ProviderType.GMAIL)
        .orElse(null);
  }

  public static int compareHistoryId(String left, String right) {
    try {
      return new BigInteger(left).compareTo(new BigInteger(right));
    } catch (RuntimeException ignored) {
      return left.compareTo(right);
    }
  }

  private String truncate(String value) {
    if (value == null || value.length() <= 1000) return value;
    return value.substring(0, 1000);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public record ClaimedRealtimeSync(
      Long connectionId,
      Long companyId,
      String gmailAccount,
      String cursorHistoryId,
      String targetHistoryId,
      int attempt) {}
}

package com.messaging.channel.microsoft;

import com.messaging.channel.microsoft.entity.MicrosoftSyncState;
import com.messaging.channel.microsoft.enums.MicrosoftSyncStatus;
import com.messaging.channel.microsoft.repository.MicrosoftSyncStateRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MicrosoftSyncStateService {

  private final MicrosoftSyncStateRepository repository;
  private final MicrosoftOAuthProperties properties;

  @Transactional
  public List<ClaimedSync> claim(int limit) {
    List<MicrosoftSyncState> states = repository.findClaimableForUpdate(Math.max(1, limit));
    Instant now = Instant.now();
    for (MicrosoftSyncState state : states) {
      state.setStatus(MicrosoftSyncStatus.RUNNING);
      state.setClaimedAt(now);
      state.setAttemptCount(state.getAttemptCount() + 1);
      state.setLastError(null);
    }
    repository.saveAll(states);
    return states.stream()
        .map(
            state ->
                new ClaimedSync(
                    state.getId(),
                    state.getConnection().getId(),
                    state.getConnection().getCompanyId(),
                    state.getFolderKey(),
                    firstNonBlank(state.getNextUrl(), state.getDeltaUrl()),
                    state.getAttemptCount()))
        .toList();
  }

  @Transactional
  public void completePage(Long stateId, String nextLink, String deltaLink) {
    MicrosoftSyncState state =
        repository.findByIdForUpdate(stateId).orElseThrow();

    state.setClaimedAt(null);
    state.setLastError(null);
    state.setNextAttemptAt(null);

    if (hasText(nextLink)) {
      state.setNextUrl(nextLink);
      state.setStatus(MicrosoftSyncStatus.PENDING);
      state.setNextAttemptAt(Instant.now());
      repository.save(state);
      return;
    }

    if (hasText(deltaLink)) {
      state.setDeltaUrl(deltaLink);
    }
    state.setNextUrl(null);
    state.setLastSyncedAt(Instant.now());
    state.setAttemptCount(0);

    if (state.isResyncRequested()) {
      state.setResyncRequested(false);
      state.setStatus(MicrosoftSyncStatus.PENDING);
      state.setNextUrl(state.getDeltaUrl());
      state.setNextAttemptAt(Instant.now());
    } else {
      state.setStatus(MicrosoftSyncStatus.SYNCED);
    }
    repository.save(state);
  }

  @Transactional
  public void resetCursor(Long stateId) {
    MicrosoftSyncState state =
        repository.findByIdForUpdate(stateId).orElseThrow();
    state.setStatus(MicrosoftSyncStatus.PENDING);
    state.setNextUrl(null);
    state.setDeltaUrl(null);
    state.setClaimedAt(null);
    state.setNextAttemptAt(Instant.now());
    state.setLastError("Microsoft delta cursor expired; restarting folder sync");
    repository.save(state);
  }

  @Transactional
  public void retry(Long stateId, String error) {
    MicrosoftSyncState state =
        repository.findByIdForUpdate(stateId).orElseThrow();
    state.setStatus(MicrosoftSyncStatus.RETRY);
    state.setClaimedAt(null);
    state.setLastError(truncate(error));
    state.setNextAttemptAt(Instant.now().plus(retryDelay(state.getAttemptCount())));
    repository.save(state);
  }

  @Transactional
  public void triggerConnection(Long connectionId) {
    for (MicrosoftSyncState state : repository.findByConnectionId(connectionId)) {
      if (state.getStatus() == MicrosoftSyncStatus.RUNNING) {
        state.setResyncRequested(true);
      } else {
        state.setStatus(MicrosoftSyncStatus.PENDING);
        state.setNextUrl(state.getDeltaUrl());
        state.setNextAttemptAt(Instant.now());
      }
      repository.save(state);
    }
  }

  @Transactional
  public int recoverStale() {
    return repository.recoverStale(Instant.now().minus(Duration.ofMinutes(10)));
  }

  private Duration retryDelay(int attempt) {
    long baseSeconds = Math.min(300, 1L << Math.min(Math.max(attempt, 1), 8));
    return Duration.ofMillis(
        baseSeconds * 1000L + ThreadLocalRandom.current().nextLong(0, 1001));
  }

  private String firstNonBlank(String first, String second) {
    return hasText(first) ? first : second;
  }

  private String truncate(String value) {
    if (value == null || value.length() <= 1000) return value;
    return value.substring(0, 1000);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public record ClaimedSync(
      Long stateId,
      Long connectionId,
      Long companyId,
      String folderKey,
      String continuationUrl,
      int attempt) {}
}

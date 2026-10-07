package com.messaging.channel.zoho;

import com.messaging.channel.enums.ProviderType;
import com.messaging.inbox.entity.ChannelSyncState;
import com.messaging.inbox.repository.ChannelSyncStateRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ZohoMailSyncStateService {

  private final ChannelSyncStateRepository repository;
  private final ZohoMailOAuthProperties properties;

  @Transactional
  public List<ClaimedIncremental> claim(int limit) {
    if (limit <= 0) return List.of();

    Instant staleCutoff = Instant.now().minus(properties.getStaleJobAfter());
    List<ChannelSyncState> states =
        repository.findZohoIncrementalClaimableForUpdate(staleCutoff, limit);
    Instant now = Instant.now();

    for (ChannelSyncState state : states) {
      state.setIncrementalClaimedAt(now);
      state.setIncrementalNextAttemptAt(null);
      state.setIncrementalAttemptCount(state.getIncrementalAttemptCount() + 1);
      state.setIncrementalLastError(null);
    }
    repository.saveAll(states);

    return states.stream()
        .map(
            state ->
                new ClaimedIncremental(
                    state.getConnection().getId(),
                    state.getConnection().getCompanyId(),
                    state.getIncrementalAttemptCount(),
                    state.getLastIncrementalSyncAt()))
        .toList();
  }

  @Transactional
  public void complete(Long connectionId, Instant syncedAt) {
    ChannelSyncState state = requireForUpdate(connectionId);
    state.setIncrementalClaimedAt(null);
    state.setIncrementalAttemptCount(0);
    state.setIncrementalLastError(null);
    state.setLastIncrementalSyncAt(syncedAt == null ? Instant.now() : syncedAt);
    state.setIncrementalNextAttemptAt(
        Instant.now().plus(properties.getIncrementalPollInterval()));
    repository.save(state);
  }

  @Transactional
  public void retry(Long connectionId, int attempt, String reason) {
    ChannelSyncState state = requireForUpdate(connectionId);
    int cappedAttempt = Math.max(1, attempt);
    state.setIncrementalClaimedAt(null);
    state.setIncrementalLastError(truncate(reason));

    if (cappedAttempt >= Math.max(1, properties.getIncrementalMaxAttempts())) {
      state.setIncrementalAttemptCount(0);
      state.setIncrementalNextAttemptAt(
          Instant.now().plus(Duration.ofMinutes(15)));
    } else {
      state.setIncrementalNextAttemptAt(
          Instant.now().plus(retryDelay(cappedAttempt)));
    }
    repository.save(state);
  }

  @Transactional
  public void trigger(Long connectionId) {
    ChannelSyncState state = requireForUpdate(connectionId);
    if (state.getIncrementalClaimedAt() == null) {
      state.setIncrementalNextAttemptAt(Instant.now());
      repository.save(state);
    }
  }

  private ChannelSyncState requireForUpdate(Long connectionId) {
    return repository
        .findForUpdate(connectionId, ProviderType.ZOHO_MAIL)
        .orElseThrow(() -> new IllegalStateException("Zoho Mail sync state not found"));
  }

  private Duration retryDelay(int attempt) {
    long seconds = Math.min(300, 1L << Math.min(Math.max(attempt, 1), 8));
    return Duration.ofMillis(
        seconds * 1000L + ThreadLocalRandom.current().nextLong(0, 1001));
  }

  private String truncate(String value) {
    if (value == null || value.length() <= 1000) return value;
    return value.substring(0, 1000);
  }

  public record ClaimedIncremental(
      Long connectionId, Long companyId, int attempt, Instant lastIncrementalSyncAt) {}
}

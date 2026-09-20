package com.messaging.channel.imapsmtp;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.entity.ChannelConnection;
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
public class ImapSmtpSyncStateService {

  private final ChannelSyncStateRepository repository;
  private final ImapSmtpCursorCodec cursorCodec;
  private final ImapSmtpProperties properties;

  @Transactional(readOnly = true)
  public ImapSmtpCursorState read(Long connectionId) {
    return repository
        .findByConnectionIdAndProviderType(connectionId, ProviderType.IMAP_SMTP)
        .map(ChannelSyncState::getCursorValue)
        .map(cursorCodec::decode)
        .orElseGet(ImapSmtpCursorState::empty);
  }

  @Transactional
  public List<ClaimedIncremental> claim(int limit) {
    if (limit <= 0) return List.of();

    Instant staleCutoff = Instant.now().minus(properties.getStaleJobAfter());
    List<ChannelSyncState> states =
        repository.findImapIncrementalClaimableForUpdate(staleCutoff, limit);
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
                    state.getIncrementalAttemptCount()))
        .toList();
  }

  @Transactional
  public void complete(Long connectionId) {
    ChannelSyncState state =
        repository
            .findForUpdate(connectionId, ProviderType.IMAP_SMTP)
            .orElseThrow(() -> new IllegalStateException("IMAP sync state not found"));
    state.setIncrementalClaimedAt(null);
    state.setIncrementalAttemptCount(0);
    state.setIncrementalLastError(null);
    state.setIncrementalNextAttemptAt(
        Instant.now().plus(properties.getIncrementalPollInterval()));
    repository.save(state);
  }

  @Transactional
  public void retry(Long connectionId, int attempt, String reason) {
    ChannelSyncState state =
        repository
            .findForUpdate(connectionId, ProviderType.IMAP_SMTP)
            .orElseThrow(() -> new IllegalStateException("IMAP sync state not found"));
    int cappedAttempt = Math.max(1, attempt);
    state.setIncrementalClaimedAt(null);
    state.setIncrementalLastError(truncate(reason));
    if (cappedAttempt >= Math.max(1, properties.getIncrementalMaxAttempts())) {
      state.setIncrementalAttemptCount(0);
      state.setIncrementalNextAttemptAt(Instant.now().plus(Duration.ofMinutes(15)));
    } else {
      state.setIncrementalNextAttemptAt(Instant.now().plus(retryDelay(cappedAttempt)));
    }
    repository.save(state);
  }

  @Transactional
  public void advance(
      ChannelConnection connection,
      EmailFolder folder,
      long uidValidity,
      long lastUid) {
    ChannelSyncState state =
        repository
            .findForUpdate(connection.getId(), ProviderType.IMAP_SMTP)
            .orElseGet(
                () -> {
                  ChannelSyncState created = new ChannelSyncState();
                  created.setConnection(connection);
                  created.setProviderType(ProviderType.IMAP_SMTP);
                  return created;
                });

    ImapSmtpCursorState current = cursorCodec.decode(state.getCursorValue());
    ImapSmtpCursorState updated =
        current.with(
            folder,
            new ImapSmtpCursorState.FolderCursor(
                Math.max(0, uidValidity), Math.max(0, lastUid)));
    state.setCursorValue(cursorCodec.encode(updated));
    state.setLastIncrementalSyncAt(Instant.now());
    repository.save(state);
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

  public record ClaimedIncremental(Long connectionId, Long companyId, int attempt) {}
}

package com.messaging.channel.microsoft;

import com.messaging.channel.email.EmailSyncRequest;
import com.messaging.channel.microsoft.entity.MicrosoftBackfillRun;
import com.messaging.channel.microsoft.enums.MicrosoftBackfillStatus;
import com.messaging.channel.microsoft.repository.MicrosoftBackfillRunRepository;
import com.messaging.common.exception.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MicrosoftBackfillStateService {

  private final MicrosoftBackfillRunRepository repository;
  private final MicrosoftOAuthProperties properties;

  @Transactional
  public MicrosoftBackfillRun createRun(
      Long jobId,
      Long companyId,
      Long userId,
      Long connectionId,
      EmailSyncRequest request) {
    MicrosoftBackfillRun run = new MicrosoftBackfillRun();
    run.setJobId(jobId);
    run.setCompanyId(companyId);
    run.setUserId(userId);
    run.setConnectionId(connectionId);
    run.setFolder(request.folder());
    run.setSyncMode(request.mode());
    run.setFromDate(request.fromDate());
    run.setToDate(request.toDate());
    run.setMessageLimit(
        request.mode() == com.messaging.channel.email.EmailSyncMode.LATEST_MESSAGES
            ? request.messageCount()
            : Math.max(1, properties.getBackfillMaxMessages()));
    run.setStatus(MicrosoftBackfillStatus.PENDING);
    run.setNextAttemptAt(Instant.now());
    return repository.saveAndFlush(run);
  }

  @Transactional(readOnly = true)
  public MicrosoftBackfillRun require(Long jobId) {
    return repository
        .findById(jobId)
        .orElseThrow(() -> new NotFoundException("Microsoft sync run not found"));
  }

  @Transactional
  public List<ClaimedBackfill> claim(int limit) {
    if (limit <= 0) return List.of();

    List<MicrosoftBackfillRun> runs = repository.findClaimableForUpdate(limit);
    Instant now = Instant.now();
    for (MicrosoftBackfillRun run : runs) {
      run.setStatus(MicrosoftBackfillStatus.RUNNING);
      run.setClaimedAt(now);
      run.setAttemptCount(run.getAttemptCount() + 1);
      run.setLastError(null);
      run.setNextAttemptAt(null);
    }
    repository.saveAll(runs);
    return runs.stream().map(this::toClaimed).toList();
  }

  @Transactional
  public Completion completePage(
      Long jobId, int processedDelta, String nextUrl) {
    MicrosoftBackfillRun run = requireForUpdate(jobId);
    long processed = run.getProcessedCount() + Math.max(0, processedDelta);
    int limit = Math.max(1, run.getMessageLimit());
    boolean reachedLimit = processed >= limit;
    boolean completed = reachedLimit || nextUrl == null || nextUrl.isBlank();

    run.setProcessedCount(Math.min(processed, limit));
    run.setClaimedAt(null);
    run.setAttemptCount(0);
    run.setLastError(null);

    if (completed) {
      run.setStatus(MicrosoftBackfillStatus.COMPLETED);
      run.setNextUrl(null);
      run.setNextAttemptAt(null);
    } else {
      run.setStatus(MicrosoftBackfillStatus.PENDING);
      run.setNextUrl(nextUrl);
      run.setNextAttemptAt(Instant.now());
    }
    repository.save(run);
    return new Completion(completed, reachedLimit, run.getProcessedCount(), limit);
  }

  @Transactional
  public void retry(Long jobId, String error) {
    MicrosoftBackfillRun run = requireForUpdate(jobId);
    run.setStatus(MicrosoftBackfillStatus.RETRY);
    run.setClaimedAt(null);
    run.setLastError(truncate(error));
    run.setNextAttemptAt(Instant.now().plus(retryDelay(run.getAttemptCount())));
    repository.save(run);
  }

  @Transactional
  public void fail(Long jobId, String error) {
    MicrosoftBackfillRun run = requireForUpdate(jobId);
    run.setStatus(MicrosoftBackfillStatus.FAILED);
    run.setClaimedAt(null);
    run.setNextAttemptAt(null);
    run.setLastError(truncate(error));
    repository.save(run);
  }

  @Transactional
  public int recoverStale() {
    return repository.recoverStale(Instant.now().minus(Duration.ofMinutes(10)));
  }

  private MicrosoftBackfillRun requireForUpdate(Long jobId) {
    return repository
        .findByJobIdForUpdate(jobId)
        .orElseThrow(() -> new NotFoundException("Microsoft sync run not found"));
  }

  private ClaimedBackfill toClaimed(MicrosoftBackfillRun run) {
    return new ClaimedBackfill(
        run.getJobId(),
        run.getCompanyId(),
        run.getUserId(),
        run.getConnectionId(),
        run.getFolder(),
        run.getSyncMode(),
        run.getFromDate(),
        run.getToDate(),
        run.getMessageLimit(),
        run.getProcessedCount(),
        run.getNextUrl(),
        run.getAttemptCount());
  }

  private Duration retryDelay(int attempt) {
    long baseSeconds = Math.min(300, 1L << Math.min(Math.max(attempt, 1), 8));
    return Duration.ofMillis(
        baseSeconds * 1000L + ThreadLocalRandom.current().nextLong(0, 1001));
  }

  private String truncate(String value) {
    if (value == null || value.length() <= 1000) return value;
    return value.substring(0, 1000);
  }

  public record ClaimedBackfill(
      Long jobId,
      Long companyId,
      Long userId,
      Long connectionId,
      com.messaging.channel.email.EmailFolder folder,
      com.messaging.channel.email.EmailSyncMode mode,
      java.time.LocalDate fromDate,
      java.time.LocalDate toDate,
      int messageLimit,
      long processedCount,
      String nextUrl,
      int attempt) {}

  public record Completion(
      boolean completed, boolean reachedLimit, long processedCount, int messageLimit) {}
}

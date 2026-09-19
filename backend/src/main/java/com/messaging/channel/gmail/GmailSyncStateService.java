package com.messaging.channel.gmail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.gmail.entity.GmailSyncBatch;
import com.messaging.channel.gmail.entity.GmailSyncRun;
import com.messaging.channel.gmail.enums.GmailSyncBatchStatus;
import com.messaging.channel.gmail.repository.GmailSyncBatchRepository;
import com.messaging.channel.gmail.repository.GmailSyncRunRepository;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GmailSyncStateService {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private final GmailSyncRunRepository runRepository;
  private final GmailSyncBatchRepository batchRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public GmailSyncRun createRun(
      Long jobId,
      Long companyId,
      Long userId,
      Long connectionId,
      LocalDate fromDate,
      LocalDate toDate) {
    GmailSyncRun run = new GmailSyncRun();
    run.setJobId(jobId);
    run.setCompanyId(companyId);
    run.setUserId(userId);
    run.setConnectionId(connectionId);
    run.setFromDate(fromDate);
    run.setToDate(toDate);
    run.setCarryIdsJson("[]");
    return runRepository.saveAndFlush(run);
  }

  @Transactional(readOnly = true)
  public GmailSyncRun requireRun(Long jobId) {
    return runRepository
        .findByJobId(jobId)
        .orElseThrow(() -> new NotFoundException("Gmail sync run not found"));
  }

  @Transactional
  public GmailSyncBatch createBatchIfAbsent(
      Long jobId, Long companyId, Long connectionId, int batchIndex, List<String> messageIds) {
    return batchRepository
        .findByJobIdAndBatchIndex(jobId, batchIndex)
        .orElseGet(
            () -> {
              GmailSyncBatch batch = new GmailSyncBatch();
              batch.setJobId(jobId);
              batch.setCompanyId(companyId);
              batch.setConnectionId(connectionId);
              batch.setBatchIndex(batchIndex);
              batch.setStatus(GmailSyncBatchStatus.PENDING);
              batch.setMessageIdsJson(writeIds(messageIds));
              batch.setMessageCount(messageIds.size());
              return batchRepository.saveAndFlush(batch);
            });
  }

  @Transactional
  public void checkpoint(
      Long jobId,
      String nextPageToken,
      int nextBatchIndex,
      List<String> carryIds,
      long discoveredCount,
      long existingCount,
      int batchCount) {
    GmailSyncRun run = requireRunForUpdate(jobId);
    run.setNextPageToken(nextPageToken);
    run.setNextBatchIndex(nextBatchIndex);
    run.setCarryIdsJson(writeIds(carryIds));
    run.setDiscoveredCount(discoveredCount);
    run.setExistingCount(existingCount);
    run.setBatchCount(batchCount);
    runRepository.save(run);
  }

  @Transactional
  public void completeDiscovery(
      Long jobId,
      int nextBatchIndex,
      long discoveredCount,
      long existingCount,
      int batchCount,
      String historyId) {
    GmailSyncRun run = requireRunForUpdate(jobId);
    run.setDiscoveryComplete(true);
    run.setNextPageToken(null);
    run.setNextBatchIndex(nextBatchIndex);
    run.setCarryIdsJson("[]");
    run.setDiscoveredCount(discoveredCount);
    run.setExistingCount(existingCount);
    run.setBatchCount(batchCount);
    run.setHistoryId(historyId);
    runRepository.save(run);
  }

  @Transactional(readOnly = true)
  public List<String> carryIds(Long jobId) {
    return readIds(requireRun(jobId).getCarryIdsJson());
  }

  @Transactional
  public List<ClaimedBatch> claimNextBatches(int limit) {
    if (limit <= 0) {
      return List.of();
    }

    List<GmailSyncBatch> batches = batchRepository.findNextClaimableForUpdate(limit);
    if (batches.isEmpty()) {
      return List.of();
    }

    List<ClaimedBatch> claimed = new java.util.ArrayList<>(batches.size());
    for (GmailSyncBatch batch : batches) {
      batch.setStatus(GmailSyncBatchStatus.RUNNING);
      batch.setAttemptCount(batch.getAttemptCount() + 1);
      batch.setLastError(null);
      claimed.add(toClaimedBatch(batch));
    }
    batchRepository.saveAll(batches);
    return claimed;
  }

  @Transactional
  public Optional<ClaimedBatch> claimBatch(Long batchId) {
    GmailSyncBatch batch =
        batchRepository
            .findByIdForUpdate(batchId)
            .orElseThrow(() -> new NotFoundException("Gmail sync batch not found"));

    if (batch.getStatus() != GmailSyncBatchStatus.PENDING
        && batch.getStatus() != GmailSyncBatchStatus.RETRY) {
      return Optional.empty();
    }

    batch.setStatus(GmailSyncBatchStatus.RUNNING);
    batch.setAttemptCount(batch.getAttemptCount() + 1);
    batch.setLastError(null);
    batchRepository.save(batch);

    return Optional.of(toClaimedBatch(batch));
  }

  @Transactional
  public boolean retryBatch(
      Long batchId,
      List<String> retryIds,
      int processedDelta,
      int failedDelta,
      String error) {
    GmailSyncBatch batch = requireBatchForUpdate(batchId);
    if (isTerminal(batch.getStatus())) {
      return false;
    }

    batch.setProcessedCount(batch.getProcessedCount() + Math.max(0, processedDelta));
    batch.setFailedCount(batch.getFailedCount() + Math.max(0, failedDelta));
    batch.setMessageIdsJson(writeIds(retryIds));
    batch.setStatus(GmailSyncBatchStatus.RETRY);
    batch.setLastError(truncate(error));
    batchRepository.save(batch);
    return true;
  }

  @Transactional
  public boolean completeBatch(
      Long batchId, int processedDelta, int failedDelta, String error) {
    GmailSyncBatch batch = requireBatchForUpdate(batchId);
    if (isTerminal(batch.getStatus())) {
      return false;
    }

    batch.setProcessedCount(batch.getProcessedCount() + Math.max(0, processedDelta));
    batch.setFailedCount(batch.getFailedCount() + Math.max(0, failedDelta));
    batch.setStatus(
        batch.getFailedCount() > 0 ? GmailSyncBatchStatus.FAILED : GmailSyncBatchStatus.COMPLETED);
    batch.setLastError(truncate(error));
    batch.setCompletedAt(Instant.now());
    batchRepository.save(batch);
    return true;
  }

  @Transactional
  public int failRemainingBatch(Long batchId, String error) {
    GmailSyncBatch batch = requireBatchForUpdate(batchId);
    if (isTerminal(batch.getStatus())) {
      return 0;
    }

    int remaining = readIds(batch.getMessageIdsJson()).size();
    batch.setProcessedCount(batch.getProcessedCount() + remaining);
    batch.setFailedCount(batch.getFailedCount() + remaining);
    batch.setStatus(GmailSyncBatchStatus.FAILED);
    batch.setLastError(truncate(error));
    batch.setCompletedAt(Instant.now());
    batchRepository.save(batch);
    return remaining;
  }

  @Transactional
  public void touchRun(Long jobId) {
    GmailSyncRun run = requireRunForUpdate(jobId);
    run.setUpdatedAt(Instant.now());
    runRepository.save(run);
  }

  @Transactional
  public boolean resetStaleForRecovery(Long batchId, Instant cutoff) {
    GmailSyncBatch batch = requireBatchForUpdate(batchId);
    if (isTerminal(batch.getStatus()) || !batch.getUpdatedAt().isBefore(cutoff)) {
      return false;
    }
    if (batch.getStatus() == GmailSyncBatchStatus.RUNNING) {
      batch.setStatus(GmailSyncBatchStatus.RETRY);
    }
    boolean recoverable =
        batch.getStatus() == GmailSyncBatchStatus.PENDING
            || batch.getStatus() == GmailSyncBatchStatus.RETRY;
    if (recoverable) {
      batch.setUpdatedAt(Instant.now());
      batchRepository.save(batch);
    }
    return recoverable;
  }

  @Transactional
  public boolean claimFinalization(Long jobId) {
    GmailSyncRun run = requireRunForUpdate(jobId);
    if (!run.isDiscoveryComplete() || run.isFinalized()) {
      return false;
    }

    long activeBatches =
        batchRepository.countByJobIdAndStatusIn(
            jobId,
            List.of(
                GmailSyncBatchStatus.PENDING,
                GmailSyncBatchStatus.RUNNING,
                GmailSyncBatchStatus.RETRY));
    if (activeBatches > 0) {
      return false;
    }

    run.setFinalized(true);
    runRepository.save(run);
    return true;
  }

  @Transactional(readOnly = true)
  public long failedCount(Long jobId) {
    return batchRepository.sumFailedCountByJobId(jobId);
  }

  private ClaimedBatch toClaimedBatch(GmailSyncBatch batch) {
    return new ClaimedBatch(
        batch.getId(),
        batch.getJobId(),
        batch.getCompanyId(),
        batch.getConnectionId(),
        batch.getAttemptCount(),
        readIds(batch.getMessageIdsJson()));
  }

  private GmailSyncRun requireRunForUpdate(Long jobId) {
    return runRepository
        .findByJobIdForUpdate(jobId)
        .orElseThrow(() -> new NotFoundException("Gmail sync run not found"));
  }

  private GmailSyncBatch requireBatchForUpdate(Long batchId) {
    return batchRepository
        .findByIdForUpdate(batchId)
        .orElseThrow(() -> new NotFoundException("Gmail sync batch not found"));
  }

  private boolean isTerminal(GmailSyncBatchStatus status) {
    return status == GmailSyncBatchStatus.COMPLETED || status == GmailSyncBatchStatus.FAILED;
  }

  private String writeIds(List<String> ids) {
    try {
      return objectMapper.writeValueAsString(ids == null ? List.of() : ids);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Gmail batch ids", exception);
    }
  }

  private List<String> readIds(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, STRING_LIST);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to deserialize Gmail batch ids", exception);
    }
  }

  private String truncate(String value) {
    if (value == null || value.length() <= 1000) {
      return value;
    }
    return value.substring(0, 1000);
  }

  public record ClaimedBatch(
      Long batchId,
      Long jobId,
      Long companyId,
      Long connectionId,
      int attempt,
      List<String> messageIds) {}
}

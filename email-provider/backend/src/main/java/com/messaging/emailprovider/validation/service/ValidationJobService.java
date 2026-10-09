package com.messaging.emailprovider.validation.service;

import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.queue.EmailOutboxService;
import com.messaging.emailprovider.validation.core.EmailNormalizer;
import com.messaging.emailprovider.validation.dto.CreateValidationJobRequest;
import com.messaging.emailprovider.validation.dto.ValidationItemResponse;
import com.messaging.emailprovider.validation.dto.ValidationJobResponse;
import com.messaging.emailprovider.validation.dto.ValidationResultsResponse;
import com.messaging.emailprovider.validation.entity.EmailValidationBatch;
import com.messaging.emailprovider.validation.entity.EmailValidationItem;
import com.messaging.emailprovider.validation.entity.EmailValidationJob;
import com.messaging.emailprovider.validation.enums.ValidationBatchStatus;
import com.messaging.emailprovider.validation.enums.ValidationJobStatus;
import com.messaging.emailprovider.validation.enums.ValidationReason;
import com.messaging.emailprovider.validation.enums.ValidationStatus;
import com.messaging.emailprovider.validation.repository.EmailValidationBatchRepository;
import com.messaging.emailprovider.validation.repository.EmailValidationItemRepository;
import com.messaging.emailprovider.validation.repository.EmailValidationJobRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValidationJobService {

  private final EmailValidationJobRepository jobs;
  private final EmailValidationBatchRepository batches;
  private final EmailValidationItemRepository items;
  private final EmailOutboxService outbox;
  private final EmailNormalizer normalizer;
  private final int batchSize;
  private final int maxAttempts;
  private final String batchTopic;
  private final String lifecycleTopic;

  public ValidationJobService(
      EmailValidationJobRepository jobs,
      EmailValidationBatchRepository batches,
      EmailValidationItemRepository items,
      EmailOutboxService outbox,
      EmailNormalizer normalizer,
      @Value("${app.validation.batch-size:100}") int batchSize,
      @Value("${app.validation.retry.max-attempts:4}") int maxAttempts,
      @Value("${app.kafka.topics.validation-batch:email.validation.batch}") String batchTopic,
      @Value("${app.kafka.topics.validation-lifecycle:email.validation.lifecycle}")
          String lifecycleTopic) {
    this.jobs = jobs;
    this.batches = batches;
    this.items = items;
    this.outbox = outbox;
    this.normalizer = normalizer;
    this.batchSize = Math.max(1, Math.min(batchSize, 500));
    this.maxAttempts = Math.max(1, maxAttempts);
    this.batchTopic = batchTopic;
    this.lifecycleTopic = lifecycleTopic;
  }

  @Transactional
  public ValidationJobResponse create(Long companyId, CreateValidationJobRequest request) {
    List<String> uniqueEmails = new ArrayList<>(request.emails().size());
    Set<String> dedupe = new HashSet<>(Math.max(16, request.emails().size() * 2));
    for (String email : request.emails()) {
      String key = normalizer.dedupeKey(email);
      if (dedupe.add(key)) {
        uniqueEmails.add(email);
      }
    }

    EmailValidationJob job = new EmailValidationJob();
    job.setCompanyId(companyId);
    job.setTotalCount(uniqueEmails.size());
    job.setDuplicateCount(request.emails().size() - uniqueEmails.size());
    job = jobs.save(job);

    int sequence = 0;
    for (int offset = 0; offset < uniqueEmails.size(); offset += batchSize) {
      int end = Math.min(offset + batchSize, uniqueEmails.size());

      EmailValidationBatch batch = new EmailValidationBatch();
      batch.setJobId(job.getId());
      batch.setBatchSequence(sequence++);
      batch.setItemCount(end - offset);
      batch = batches.save(batch);

      List<EmailValidationItem> rows = new ArrayList<>(end - offset);
      for (int index = offset; index < end; index++) {
        String email = uniqueEmails.get(index);
        EmailValidationItem item = new EmailValidationItem();
        item.setJobId(job.getId());
        item.setBatchId(batch.getId());
        item.setOriginalEmail(email);
        item.setDedupeKey(normalizer.dedupeKey(email));
        rows.add(item);
      }
      items.saveAll(rows);
      outbox.enqueue(batchTopic, batch.getId().toString(), batch.getId().toString());
    }

    job.setTotalBatches(sequence);
    if (sequence == 0) {
      job.setStatus(ValidationJobStatus.COMPLETED);
      job.setCompletedAt(Instant.now());
    }
    job = jobs.save(job);
    emitLifecycle(job, "JOB_ACCEPTED");
    return ValidationJobResponse.from(job);
  }

  @Transactional(readOnly = true)
  public ValidationJobResponse get(Long companyId, Long jobId) {
    return ValidationJobResponse.from(requireJob(companyId, jobId));
  }

  @Transactional(readOnly = true)
  public ValidationResultsResponse results(Long companyId, Long jobId, int page, int size) {
    requireJob(companyId, jobId);
    int safeSize = Math.max(1, Math.min(size, 200));
    int safePage = Math.max(0, page);
    Page<EmailValidationItem> result =
        items.findAllByJobId(
            jobId, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "id")));
    return new ValidationResultsResponse(
        result.getContent().stream().map(ValidationItemResponse::from).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional
  public boolean claimBatch(Long batchId) {
    EmailValidationBatch batch = batches.findLockedById(batchId).orElse(null);
    if (batch == null
        || batch.getStatus() == ValidationBatchStatus.COMPLETED
        || batch.getStatus() == ValidationBatchStatus.FAILED
        || batch.getStatus() == ValidationBatchStatus.PROCESSING) {
      return false;
    }
    if (batch.getStatus() == ValidationBatchStatus.RETRY
        && batch.getNextAttemptAt() != null
        && batch.getNextAttemptAt().isAfter(Instant.now())) {
      return false;
    }

    batch.setStatus(ValidationBatchStatus.PROCESSING);
    batch.setAttempts(batch.getAttempts() + 1);
    batch.setStartedAt(Instant.now());
    batch.setNextAttemptAt(null);
    batches.save(batch);

    EmailValidationJob job = jobs.findLockedById(batch.getJobId()).orElseThrow();
    if (job.getStatus() == ValidationJobStatus.QUEUED) {
      job.setStatus(ValidationJobStatus.PROCESSING);
      job.setStartedAt(Instant.now());
      jobs.save(job);
      emitLifecycle(job, "JOB_STARTED");
    }
    return true;
  }

  @Transactional
  public void completeBatch(Long batchId, ValidationBatchStats stats) {
    EmailValidationBatch batch = batches.findLockedById(batchId).orElseThrow();
    if (batch.getStatus() != ValidationBatchStatus.PROCESSING) {
      return;
    }

    batch.setStatus(ValidationBatchStatus.COMPLETED);
    batch.setCompletedAt(Instant.now());
    batch.setLastError(null);
    batches.save(batch);

    EmailValidationJob job = jobs.findLockedById(batch.getJobId()).orElseThrow();
    job.setProcessedCount(job.getProcessedCount() + stats.processed());
    job.setValidCount(job.getValidCount() + stats.valid());
    job.setInvalidCount(job.getInvalidCount() + stats.invalid());
    job.setRiskyCount(job.getRiskyCount() + stats.risky());
    job.setUnknownCount(job.getUnknownCount() + stats.unknown());
    job.setCompletedBatches(job.getCompletedBatches() + 1);

    boolean finished = finishIfDone(job);
    jobs.save(job);
    if (finished) {
      emitLifecycle(job, "JOB_" + job.getStatus().name());
    }
  }

  @Transactional
  public void failBatch(Long batchId, String error) {
    EmailValidationBatch batch = batches.findLockedById(batchId).orElse(null);
    if (batch == null || batch.getStatus() != ValidationBatchStatus.PROCESSING) {
      return;
    }

    batch.setLastError(truncate(error));
    if (batch.getAttempts() < maxAttempts) {
      long delaySeconds = Math.min(900L, 5L << Math.min(batch.getAttempts() - 1, 7));
      batch.setStatus(ValidationBatchStatus.RETRY);
      batch.setNextAttemptAt(Instant.now().plusSeconds(delaySeconds));
      batches.save(batch);
      return;
    }

    batch.setStatus(ValidationBatchStatus.FAILED);
    batch.setCompletedAt(Instant.now());
    batches.save(batch);

    List<EmailValidationItem> failedItems = items.findAllByBatchIdOrderByIdAsc(batchId);
    Instant now = Instant.now();
    long valid = 0;
    long invalid = 0;
    long risky = 0;
    long unknown = 0;
    for (EmailValidationItem item : failedItems) {
      if (item.getStatus() == null) {
        item.setStatus(ValidationStatus.UNKNOWN);
        item.setReason(ValidationReason.VALIDATION_INTERNAL_ERROR);
        item.setScore(0);
        item.setValidatedAt(now);
      }
      switch (item.getStatus()) {
        case VALID -> valid++;
        case INVALID -> invalid++;
        case RISKY -> risky++;
        case UNKNOWN -> unknown++;
      }
    }
    items.saveAll(failedItems);

    EmailValidationJob job = jobs.findLockedById(batch.getJobId()).orElseThrow();
    job.setProcessedCount(job.getProcessedCount() + batch.getItemCount());
    job.setValidCount(job.getValidCount() + valid);
    job.setInvalidCount(job.getInvalidCount() + invalid);
    job.setRiskyCount(job.getRiskyCount() + risky);
    job.setUnknownCount(job.getUnknownCount() + unknown);
    job.setFailedBatches(job.getFailedBatches() + 1);

    boolean finished = finishIfDone(job);
    jobs.save(job);
    if (finished) {
      emitLifecycle(job, "JOB_" + job.getStatus().name());
    }
  }

  @Transactional
  public int requeueDueRetries(int limit) {
    List<EmailValidationBatch> due =
        batches.findDueRetries(Instant.now(), Math.max(1, Math.min(limit, 200)));
    for (EmailValidationBatch batch : due) {
      batch.setStatus(ValidationBatchStatus.PENDING);
      batch.setNextAttemptAt(null);
      batches.save(batch);
      outbox.enqueue(batchTopic, batch.getId().toString(), batch.getId().toString());
    }
    return due.size();
  }

  private boolean finishIfDone(EmailValidationJob job) {
    int finished = job.getCompletedBatches() + job.getFailedBatches();
    if (finished < job.getTotalBatches()) {
      return false;
    }

    job.setCompletedAt(Instant.now());
    if (job.getFailedBatches() == 0) {
      job.setStatus(ValidationJobStatus.COMPLETED);
    } else if (job.getCompletedBatches() == 0) {
      job.setStatus(ValidationJobStatus.FAILED);
    } else {
      job.setStatus(ValidationJobStatus.PARTIAL_FAILED);
    }
    return true;
  }

  private void emitLifecycle(EmailValidationJob job, String eventType) {
    String payload =
        "{"
            + "\"eventType\":\""
            + eventType
            + "\","
            + "\"jobId\":\""
            + job.getId()
            + "\","
            + "\"companyId\":\""
            + job.getCompanyId()
            + "\","
            + "\"status\":\""
            + job.getStatus()
            + "\","
            + "\"occurredAt\":\""
            + Instant.now()
            + "\""
            + "}";
    outbox.enqueue(lifecycleTopic, job.getId().toString(), payload);
  }

  private EmailValidationJob requireJob(Long companyId, Long jobId) {
    return jobs
        .findByIdAndCompanyId(jobId, companyId)
        .orElseThrow(() -> new NotFoundException("Validation job not found"));
  }

  private String truncate(String value) {
    if (value == null) {
      return "Validation batch failed";
    }
    return value.length() <= 2048 ? value : value.substring(0, 2048);
  }
}

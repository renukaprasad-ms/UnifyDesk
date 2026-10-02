package com.messaging.contacts.audience;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.audience.AudienceSourceResolver.*;
import com.messaging.contacts.consent.*;
import com.messaging.contacts.consent.ConsentTypes.*;
import com.messaging.contacts.integration.ContactCoreReadPort;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AudienceSnapshotService {

  private final AudienceSourceResolver sourceResolver;
  private final AudienceSnapshotRepository repository;
  private final ContactCoreReadPort coreReadPort;
  private final ContactEligibilityService eligibilityService;
  private final ContactAudienceProperties properties;
  private final AsyncJobService asyncJobs;

  @Transactional
  public SnapshotJob create(
      long companyId,
      long userId,
      SourceType sourceType,
      long sourceId,
      Channel channel,
      Purpose purpose) {
    SourceDescriptor source = sourceResolver.describe(companyId, sourceType, sourceId);
    long snapshotId = IdGenerator.nextId();
    AsyncJobResponse job =
        asyncJobs.create(
            companyId,
            userId,
            AsyncJobType.CONTACT_AUDIENCE_SNAPSHOT,
            Long.toString(snapshotId),
            null,
            "Audience snapshot queued");
    AudienceSnapshotRepository.Snapshot snapshot =
        repository.create(
            snapshotId,
            companyId,
            sourceType.name(),
            sourceId,
            source.sourceVersion(),
            source.filterHash(),
            channel,
            purpose,
            Long.parseLong(job.jobId()));
    sourceResolver.captureCandidates(companyId, source, snapshot.id());
    return new SnapshotJob(snapshot, job);
  }

  @Transactional
  public AudienceSnapshotRepository.Snapshot createForBroadcast(
      long companyId, SourceType sourceType, long sourceId, Channel channel, Purpose purpose) {
    return createForBroadcast(
        companyId,
        sourceResolver.describeForBroadcast(companyId, sourceType, sourceId),
        channel,
        purpose);
  }

  @Transactional
  public AudienceSnapshotRepository.Snapshot createForBroadcast(
      long companyId,
      BroadcastSourceState sourceState,
      Channel channel,
      Purpose purpose) {
    SourceDescriptor source = sourceState.source();
    AudienceSnapshotRepository.Snapshot snapshot =
        repository.create(
            IdGenerator.nextId(),
            companyId,
            source.type().name(),
            source.id(),
            source.sourceVersion(),
            source.filterHash(),
            channel,
            purpose,
            null);
    sourceResolver.captureCandidates(companyId, sourceState, snapshot.id());
    return snapshot;
  }

  @Transactional
  public boolean processBatch(long companyId, long snapshotId) {
    AudienceSnapshotRepository.Snapshot snapshot = repository.requireForUpdate(companyId, snapshotId);
    if (!"PREPARING".equals(snapshot.status())) return false;
    List<Long> candidates =
        repository.pageCandidates(
            companyId, snapshot.id(), snapshot.cursor(), properties.boundedBatchSize());
    if (candidates.isEmpty()) {
      repository.ready(companyId, snapshotId);
      if (snapshot.asyncJobId() != null)
        asyncJobs.complete(snapshot.asyncJobId(), "Audience snapshot ready");
      return false;
    }

    Map<Long, EligibilityResult> decisions =
        eligibilityService.evaluateBatch(
            companyId,
            candidates,
            snapshot.channel(),
            snapshot.purpose(),
            new EligibilityContext(null, null, null));

    List<AudienceSnapshotRepository.Member> members = new ArrayList<>();
    long sequence = snapshot.eligibleCount();
    for (Long contactId : candidates) {
      EligibilityResult decision = decisions.get(contactId);
      if (decision == null
          || decision.decision() != EligibilityDecision.ELIGIBLE
          || decision.identityId() == null
          || decision.normalizedDestination() == null) continue;
      sequence++;
      members.add(
          new AudienceSnapshotRepository.Member(
              contactId,
              decision.identityId(),
              decision.normalizedDestination(),
              null,
              decision.policyVersion(),
              decision.reason().name(),
              sequence));
    }
    int inserted = repository.insertMembers(snapshotId, companyId, members);
    long newCursor = candidates.getLast();
    repository.advance(
        companyId,
        snapshotId,
        snapshot.cursor(),
        newCursor,
        candidates.size(),
        inserted);
    if (snapshot.asyncJobId() != null) {
      asyncJobs.incrementProgress(
          snapshot.asyncJobId(), candidates.size(), "Audience snapshot running");
    }
    return true;
  }

  public AudienceSnapshotRepository.Snapshot get(long companyId, long snapshotId) {
    return repository.require(companyId, snapshotId);
  }

  public long candidateCount(long companyId, long snapshotId) {
    return repository.candidateCount(companyId, snapshotId);
  }

  public void fail(long companyId, long snapshotId) {
    repository.fail(companyId, snapshotId);
  }

  public List<AudienceSnapshotRepository.MemberView> pageMembers(
      long companyId, long snapshotId, long afterSequenceId, int limit) {
    return repository.pageMembers(companyId, snapshotId, afterSequenceId, limit);
  }

  public record SnapshotJob(AudienceSnapshotRepository.Snapshot snapshot, AsyncJobResponse job) {}
}

package com.messaging.contacts.audience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.billing.service.EntitlementService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.contacts.audience.SegmentCompiler.CompiledSegment;
import com.messaging.contacts.audience.SegmentCompiler.CostClass;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SegmentService {

  private final SegmentCompiler compiler;
  private final SegmentRepository repository;
  private final SegmentSampleRepository sampleRepository;
  private final ObjectMapper objectMapper;
  private final EntitlementService entitlementService;
  private final AsyncJobService asyncJobService;
  private final ContactAudienceProperties properties;

  public ValidationResult validate(long companyId, SegmentFilter filter) {
    CompiledSegment compiled = compiler.compile(companyId, filter);
    return new ValidationResult(filter, compiled.filterHash(), compiled.costClass());
  }

  @Transactional
  public SegmentResponse create(
      long companyId, long userId, String name, String description, SegmentFilter filter) {
    requireName(name);
    entitlementService.assertWithinLimit(
        companyId, "SEGMENT_LIMIT", repository.countActive(companyId));
    CompiledSegment compiled = compiler.compile(companyId, filter);
    return response(
        repository.create(
            companyId,
            userId,
            name,
            description,
            filter.version(),
            json(filter),
            compiled.filterHash()));
  }

  public List<SegmentResponse> list(long companyId) {
    return repository.list(companyId).stream().map(this::response).toList();
  }

  public SegmentResponse get(long companyId, long segmentId) {
    return response(repository.require(companyId, segmentId));
  }

  @Transactional
  public SegmentResponse update(
      long companyId,
      long segmentId,
      long expectedVersion,
      String name,
      String description,
      SegmentFilter filter) {
    requireName(name);
    CompiledSegment compiled = compiler.compile(companyId, filter);
    if (!repository.update(
        companyId,
        segmentId,
        expectedVersion,
        name,
        description,
        filter.version(),
        json(filter),
        compiled.filterHash())) {
      if (repository.find(companyId, segmentId).isEmpty()) {
        throw new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND");
      }
      throw new ConflictException("CONTACT_VERSION_CONFLICT");
    }
    return response(repository.require(companyId, segmentId));
  }

  @Transactional
  public void archive(long companyId, long segmentId) {
    if (!repository.archive(companyId, segmentId)) {
      throw new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND");
    }
  }

  public PreviewResult preview(long companyId, SegmentFilter filter, Integer requestedSampleSize) {
    CompiledSegment compiled = compiler.compile(companyId, filter);
    int sampleSize =
        Math.max(
            1,
            Math.min(
                requestedSampleSize == null ? 20 : requestedSampleSize,
                Math.min(20, properties.getPageMaxSize())));
    List<SegmentSampleRepository.SampleRow> sample = sampleRepository.page(compiled, sampleSize);
    if (compiled.costClass() == CostClass.LOW) {
      long count = repository.count(compiled);
      return new PreviewResult(sample, count, "EXACT", Instant.now());
    }
    return new PreviewResult(sample, null, "PENDING", null);
  }

  @Transactional
  public AsyncJobResponse refreshCount(long companyId, long userId, long segmentId) {
    SegmentRepository.SegmentRow segment = repository.require(companyId, segmentId);
    if (!"ACTIVE".equals(segment.status())) {
      throw new BadRequestException("CONTACT_SEGMENT_INVALID");
    }
    return asyncJobService.create(
        companyId,
        userId,
        AsyncJobType.CONTACT_SEGMENT_COUNT,
        Long.toString(segmentId),
        null,
        "Segment count queued");
  }

  public void processCountJob(long companyId, long segmentId) {
    SegmentRepository.SegmentRow row = repository.require(companyId, segmentId);
    SegmentFilter filter = parse(row.filterJson());
    CompiledSegment compiled = compiler.compile(companyId, filter);
    if (!compiled.filterHash().equals(row.filterHash())) {
      throw new IllegalStateException("Segment filter hash mismatch");
    }
    repository.updateCount(companyId, segmentId, row.filterHash(), repository.count(compiled));
  }

  private SegmentResponse response(SegmentRepository.SegmentRow row) {
    return new SegmentResponse(
        Long.toString(row.id()),
        row.name(),
        row.description(),
        row.filterVersion(),
        parse(row.filterJson()),
        row.filterHash(),
        row.status(),
        row.cachedCount(),
        row.countComputedAt(),
        row.version(),
        row.createdAt(),
        row.updatedAt());
  }

  private String json(SegmentFilter filter) {
    try {
      return objectMapper.writeValueAsString(filter);
    } catch (JsonProcessingException exception) {
      throw new BadRequestException("CONTACT_SEGMENT_INVALID");
    }
  }

  private SegmentFilter parse(String json) {
    try {
      return objectMapper.readValue(json, SegmentFilter.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored segment filter is invalid", exception);
    }
  }

  private void requireName(String name) {
    if (name == null || name.isBlank() || name.trim().length() > 160) {
      throw new BadRequestException("CONTACT_SEGMENT_INVALID");
    }
  }

  public record ValidationResult(
      SegmentFilter normalizedFilter, String filterHash, CostClass costClass) {}

  public record PreviewResult(
      List<SegmentSampleRepository.SampleRow> sample,
      Long count,
      String countState,
      Instant countComputedAt) {}

  public record SegmentResponse(
      String id,
      String name,
      String description,
      int filterVersion,
      SegmentFilter filter,
      String filterHash,
      String status,
      Long cachedCount,
      Instant countComputedAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {}
}

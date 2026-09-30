package com.messaging.contacts.processing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.contacts.processing.ContactExportRowSource.*;
import com.messaging.contacts.processing.ContactStorageAdapter.StorageRef;
import com.messaging.media.provider.SignedDownloadResult;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactExportService {

  private final ContactExportRepository repository;
  private final ContactExportRowSource rowSource;
  private final AsyncJobService asyncJobs;
  private final ContactAudienceProperties properties;
  private final ContactStorageAdapter storage;
  private final ObjectMapper objectMapper;

  @Transactional
  public CreateResult create(
      long companyId,
      long userId,
      Source source,
      List<String> fields,
      String idempotencyKey) {
    if (source == null
        || source.type() == null
        || idempotencyKey == null
        || idempotencyKey.isBlank()) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_FIELD_INVALID");
    }
    rowSource.validateSource(companyId, source);
    rowSource.validateFields(fields);
    repository.lockIdempotency(companyId, idempotencyKey);
    var existing = repository.findByIdempotency(companyId, idempotencyKey);
    if (existing.isPresent()) return new CreateResult(existing.get(), null);

    long exportId = IdGenerator.nextId();
    AsyncJobResponse job =
        asyncJobs.create(
            companyId,
            userId,
            AsyncJobType.CONTACT_EXPORT,
            Long.toString(exportId),
            null,
            "Contact export queued");
    ContactExportRepository.ExportRow row =
        repository.create(
            exportId,
            companyId,
            userId,
            source.type().name(),
            source.id(),
            json(fields),
            idempotencyKey,
            Long.parseLong(job.jobId()),
            Instant.now().plus(properties.getArtifactTtl()));
    return new CreateResult(row, job);
  }

  public ContactExportRepository.ExportRow get(long companyId, long exportId) {
    return repository.require(companyId, exportId);
  }

  public Download download(long companyId, long exportId) {
    ContactExportRepository.ExportRow row = repository.require(companyId, exportId);
    if (!"COMPLETED".equals(row.status())
        || row.outputProvider() == null
        || row.expiresAt().isBefore(Instant.now())) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_EXPORT_NOT_READY");
    }
    SignedDownloadResult signed =
        storage.signedDownload(
            new StorageRef(row.outputProvider(), row.outputBucket(), row.outputObjectKey()));
    return new Download(signed.url(), signed.expiresAt());
  }

  public List<String> parseFields(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception exception) {
      throw new IllegalStateException("Stored export field selection is invalid", exception);
    }
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_FIELD_INVALID");
    }
  }

  public record CreateResult(ContactExportRepository.ExportRow export, AsyncJobResponse job) {}

  public record Download(String url, Instant expiresAt) {}
}

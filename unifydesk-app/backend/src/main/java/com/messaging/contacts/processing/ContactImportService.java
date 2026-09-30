package com.messaging.contacts.processing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.contacts.processing.ContactImportMutationPort.*;
import com.messaging.contacts.processing.ContactStorageAdapter.*;
import com.messaging.media.provider.StorageObjectMetadata;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactImportService {

  private final ContactImportRepository repository;
  private final ContactStorageAdapter storage;
  private final ContactAudienceProperties properties;
  private final ObjectMapper objectMapper;
  private final AsyncJobService asyncJobs;

  public ImportIntent initiate(long companyId, long userId, long expectedBytes) {
    long id = IdGenerator.nextId();
    String objectKey = "contacts/" + companyId + "/imports/" + id + "/source.csv";
    UploadIntent upload = storage.createImportUpload(companyId, id, expectedBytes, objectKey);
    repository.create(
        id,
        companyId,
        userId,
        upload.provider(),
        upload.bucket(),
        upload.objectKey(),
        expectedBytes,
        Instant.now().plus(properties.getArtifactTtl()));
    return new ImportIntent(Long.toString(id), upload);
  }

  public Preview preview(long companyId, long importId) {
    ContactImportRepository.ImportRow row = repository.require(companyId, importId);
    StorageRef ref = ref(row);
    StorageObjectMetadata metadata = storage.metadata(ref);
    validateUploaded(row, metadata);
    repository.markUploaded(companyId, importId);
    try (InputStream input = storage.openStream(ref);
        Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        CSVParser parser =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(false)
                .get()
                .parse(reader)) {
      List<String> headers = parser.getHeaderNames();
      if (headers.isEmpty() || headers.size() > properties.getCsvMaxColumns()) throw invalid();
      List<List<String>> sample = new ArrayList<>();
      for (CSVRecord record : parser) {
        if (sample.size() >= properties.getCsvPreviewRows()) break;
        if (record.size() > properties.getCsvMaxColumns()) throw invalid();
        List<String> cells = new ArrayList<>(record.size());
        for (String value : record) {
          CsvSafety.validateCell(value, properties.getCsvMaxCellChars());
          cells.add(value);
        }
        sample.add(cells);
      }
      repository.markPreviewed(companyId, importId);
      return new Preview(headers, sample);
    } catch (IOException | IllegalArgumentException exception) {
      throw invalid();
    }
  }

  public CommitResult commit(
      long companyId,
      long userId,
      long importId,
      ImportMapping mapping,
      DuplicateStrategy duplicateStrategy,
      Long targetListId,
      String idempotencyKey) {
    if (mapping == null
        || duplicateStrategy == null
        || idempotencyKey == null
        || idempotencyKey.isBlank()) throw invalid();

    ContactImportRepository.ImportRow row = repository.require(companyId, importId);
    StorageObjectMetadata metadata = storage.metadata(ref(row));
    validateUploaded(row, metadata);
    String checksum = sha256(ref(row));
    String mappingJson = json(mapping);
    String mappingHash = sha256(mappingJson.getBytes(StandardCharsets.UTF_8));

    AsyncJobResponse job =
        asyncJobs.create(
            companyId,
            userId,
            AsyncJobType.CONTACT_IMPORT,
            Long.toString(importId),
            null,
            "Contact import queued");
    ContactImportRepository.QueueResult queued =
        repository.queue(
            companyId,
            importId,
            checksum,
            mappingJson,
            mappingHash,
            duplicateStrategy.name(),
            targetListId,
            idempotencyKey,
            Long.parseLong(job.jobId()));
    if (queued.newlyQueued()) {
      storage.commitImportReservation(companyId, importId, row.expectedSizeBytes());
    }
    return new CommitResult(queued.row(), job);
  }

  public ContactImportRepository.ImportRow get(long companyId, long importId) {
    return repository.require(companyId, importId);
  }

  public ImportMapping parseMapping(String json) {
    try {
      return objectMapper.readValue(json, ImportMapping.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored import mapping is invalid", exception);
    }
  }

  public String sha256(StorageRef ref) {
    try (InputStream input = storage.openStream(ref)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[64 * 1024];
      int read;
      while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
      return HexFormat.of().formatHex(digest.digest());
    } catch (Exception exception) {
      if (exception instanceof RuntimeException runtime) throw runtime;
      throw new IllegalStateException("Unable to checksum import", exception);
    }
  }

  public StorageRef ref(ContactImportRepository.ImportRow row) {
    return new StorageRef(row.provider(), row.bucket(), row.objectKey());
  }

  private void validateUploaded(
      ContactImportRepository.ImportRow row, StorageObjectMetadata metadata) {
    if (!metadata.exists()
        || metadata.sizeBytes() == null
        || metadata.sizeBytes() <= 0
        || metadata.sizeBytes() > properties.getCsvMaxBytes()
        || metadata.sizeBytes() > row.expectedSizeBytes()) throw invalid();
    String type = metadata.contentType();
    if (type != null
        && !type.isBlank()
        && !type.toLowerCase(Locale.ROOT).contains("csv")
        && !type.toLowerCase(Locale.ROOT).contains("text/plain")
        && !type.toLowerCase(Locale.ROOT).contains("octet-stream")) throw invalid();
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw invalid();
    }
  }

  private String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private BadRequestException invalid() {
    return new BadRequestException("CONTACT_IMPORT_INVALID");
  }

  public record ImportIntent(String importId, UploadIntent upload) {}

  public record Preview(List<String> headers, List<List<String>> sampleRows) {}

  public record CommitResult(ContactImportRepository.ImportRow importRow, AsyncJobResponse job) {}
}

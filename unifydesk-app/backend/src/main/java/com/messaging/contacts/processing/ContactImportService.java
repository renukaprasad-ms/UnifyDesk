package com.messaging.contacts.processing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.contacts.processing.ContactImportMutationPort.DuplicateStrategy;
import com.messaging.contacts.processing.ContactImportMutationPort.ImportMapping;
import com.messaging.contacts.processing.ContactStorageAdapter.StorageRef;
import com.messaging.contacts.processing.ContactStorageAdapter.UploadIntent;
import com.messaging.media.provider.StorageObjectMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactImportService {

  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("text/csv", "text/plain", "application/vnd.ms-excel", "application/octet-stream");

  private final ContactImportRepository repository;
  private final ContactStorageAdapter storage;
  private final ContactAudienceProperties properties;
  private final ObjectMapper objectMapper;
  private final ContactImportCommitCoordinator commitCoordinator;

  public ImportIntent initiate(
      long companyId,
      long userId,
      String fileName,
      String contentType,
      long expectedBytes) {
    String safeFileName = safeFileName(fileName);
    String safeContentType = safeContentType(contentType);
    long id = IdGenerator.nextId();
    String objectKey = "contacts/" + companyId + "/imports/" + id + "/source.csv";
    UploadIntent upload = storage.createImportUpload(companyId, id, expectedBytes, objectKey);
    Instant createdAt = Instant.now();
    repository.create(
        id,
        companyId,
        userId,
        safeFileName,
        safeContentType,
        upload.provider(),
        upload.bucket(),
        upload.objectKey(),
        expectedBytes,
        createdAt.plus(properties.getArtifactTtl()));
    return new ImportIntent(
        Long.toString(id), safeFileName, upload, properties.getCsvMaxBytes(), createdAt);
  }

  public Preview preview(long companyId, long importId) {
    ContactImportRepository.ImportRow row = repository.require(companyId, importId);
    StorageRef ref = ref(row);
    StorageObjectMetadata metadata = storage.metadata(ref);
    validateUploaded(row, metadata);
    repository.markUploaded(companyId, importId);
    try (InputStream input = storage.openStream(ref);
        Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        CSVParser parser = csvParser(reader)) {
      List<String> headers = validateHeaders(parser.getHeaderNames());
      List<List<String>> sample = new java.util.ArrayList<>();
      for (CSVRecord record : parser) {
        if (sample.size() >= properties.getCsvPreviewRows()) break;
        if (record.size() > properties.getCsvMaxColumns()) throw invalid();
        List<String> cells = new java.util.ArrayList<>(record.size());
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

  public List<String> headers(long companyId, long importId) {
    ContactImportRepository.ImportRow row = repository.require(companyId, importId);
    StorageObjectMetadata metadata = storage.metadata(ref(row));
    validateUploaded(row, metadata);
    try (InputStream input = storage.openStream(ref(row));
        Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        CSVParser parser = csvParser(reader)) {
      return validateHeaders(parser.getHeaderNames());
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
        || idempotencyKey.isBlank()
        || idempotencyKey.length() > 160) {
      throw invalid();
    }

    ContactImportRepository.ImportRow row = repository.require(companyId, importId);
    StorageObjectMetadata metadata = storage.metadata(ref(row));
    validateUploaded(row, metadata);
    String checksum = sha256(ref(row));
    String mappingJson = json(mapping);
    String mappingHash = sha256(mappingJson.getBytes(StandardCharsets.UTF_8));

    return commitCoordinator.queue(
        companyId,
        userId,
        importId,
        checksum,
        mappingJson,
        mappingHash,
        duplicateStrategy,
        targetListId,
        idempotencyKey,
        metadata.sizeBytes());
  }

  public ContactImportRepository.ImportRow get(long companyId, long importId) {
    return repository.require(companyId, importId);
  }

  public ErrorReport errors(long companyId, long importId) {
    ContactImportRepository.ImportRow row = repository.require(companyId, importId);
    boolean reportReady =
        row.errorReportProvider() != null
            && row.errorReportBucket() != null
            && row.errorReportObjectKey() != null;
    boolean terminal =
        Set.of("COMPLETED", "FAILED", "VALIDATION_FAILED", "EXPIRED").contains(row.status());
    if (!reportReady) {
      return new ErrorReport(row.failedRows(), terminal && row.failedRows() == 0, null, null);
    }
    var signed =
        storage.signedDownload(
            new StorageRef(
                row.errorReportProvider(),
                row.errorReportBucket(),
                row.errorReportObjectKey()));
    return new ErrorReport(row.failedRows(), true, signed.url(), signed.expiresAt());
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
      while ((read = input.read(buffer)) >= 0) {
        if (read > 0) digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (Exception exception) {
      if (exception instanceof RuntimeException runtime) throw runtime;
      throw new IllegalStateException("Unable to checksum import", exception);
    }
  }

  public StorageRef ref(ContactImportRepository.ImportRow row) {
    return new StorageRef(row.provider(), row.bucket(), row.objectKey());
  }

  private CSVParser csvParser(Reader reader) throws IOException {
    return CSVFormat.DEFAULT
        .builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setIgnoreEmptyLines(false)
        .build()
        .parse(reader);
  }

  private List<String> validateHeaders(List<String> headers) {
    if (headers == null
        || headers.isEmpty()
        || headers.size() > properties.getCsvMaxColumns()
        || headers.stream().anyMatch(value -> value == null || value.isBlank())
        || headers.stream().distinct().count() != headers.size()) {
      throw invalid();
    }
    List<String> normalized = new java.util.ArrayList<>(headers);
    String first = normalized.getFirst();
    if (!first.isEmpty() && first.charAt(0) == '\uFEFF') {
      normalized.set(0, first.substring(1));
    }
    if (normalized.getFirst().isBlank() || normalized.stream().distinct().count() != normalized.size()) {
      throw invalid();
    }
    return List.copyOf(normalized);
  }

  private void validateUploaded(
      ContactImportRepository.ImportRow row, StorageObjectMetadata metadata) {
    if (!metadata.exists()
        || metadata.sizeBytes() == null
        || metadata.sizeBytes() <= 0
        || metadata.sizeBytes() > properties.getCsvMaxBytes()
        || metadata.sizeBytes() > row.expectedSizeBytes()) {
      throw invalid();
    }
    String type = metadata.contentType();
    if (type != null
        && !type.isBlank()
        && !type.toLowerCase(Locale.ROOT).contains("csv")
        && !type.toLowerCase(Locale.ROOT).contains("text/plain")
        && !type.toLowerCase(Locale.ROOT).contains("octet-stream")) {
      throw invalid();
    }
  }

  private String safeFileName(String value) {
    if (value == null || value.isBlank()) throw invalid();
    String normalized = value.replace('\\', '/');
    normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
    normalized = normalized.replaceAll("[\\p{Cntrl}]", "");
    if (normalized.isBlank() || normalized.length() > 255) throw invalid();
    return normalized;
  }

  private String safeContentType(String value) {
    String normalized =
        value == null || value.isBlank()
            ? "text/csv"
            : value.trim().toLowerCase(Locale.ROOT);
    if (!ALLOWED_CONTENT_TYPES.contains(normalized)) throw invalid();
    return normalized;
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

  public record ImportIntent(
      String importId,
      String fileName,
      UploadIntent upload,
      long maxBytes,
      Instant createdAt) {}

  public record Preview(List<String> headers, List<List<String>> sampleRows) {}

  public record CommitResult(ContactImportRepository.ImportRow importRow, AsyncJobResponse job) {}

  public record ErrorReport(
      long count, boolean ready, String downloadUrl, Instant expiresAt) {}
}

package com.messaging.contacts.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import com.messaging.contacts.processing.ContactExportRowSource.SourceType;
import com.messaging.contacts.processing.ContactImportMutationPort.DuplicateStrategy;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ContactProcessingApiModel {

  private ContactProcessingApiModel() {}

  public record JobResponse(
      String id,
      String jobId,
      String status,
      Integer progressPercent,
      Long processedCount,
      Long successCount,
      Long errorCount,
      String message,
      Instant createdAt,
      Instant updatedAt) {}

  public record ImportCreateRequest(String fileName, String contentType, long sizeBytes) {}

  public record ImportUploadContract(
      String method, String url, Map<String, String> headers) {}

  public record ContactImportResponse(
      String id,
      String status,
      String fileName,
      ImportUploadContract upload,
      String serverUploadUrl,
      Long maxBytes,
      JobResponse job,
      long totalRows,
      long successRows,
      long errorRows,
      String targetListId,
      Instant createdAt,
      Instant updatedAt) {}

  public record SuggestedMapping(String column, String target) {}

  public record ImportPreviewResponse(
      List<String> headers,
      List<List<String>> sampleRows,
      String detectedDelimiter,
      String encoding,
      Long estimatedRows,
      List<String> validationWarnings,
      List<SuggestedMapping> suggestedMappings) {}

  public enum ImportTargetType {
    IGNORE,
    BUILTIN,
    IDENTITY,
    TAG_NAMES,
    CUSTOM_FIELD
  }

  public record ImportFieldTarget(
      ImportTargetType type, String field, String identityType, String fieldId) {}

  public record ImportFieldMapping(String column, ImportFieldTarget target) {}

  public record ConsentImportMapping(
      Channel channel,
      String stateColumn,
      PreferenceSource source,
      String evidenceColumn,
      String occurredAtColumn) {}

  public record ConsentImportDeclaration(
      Channel channel,
      PreferenceState state,
      PreferenceSource source,
      String evidence,
      Instant occurredAt,
      boolean confirmed) {}

  public record ImportCommitRequest(
      List<ImportFieldMapping> mappings,
      DuplicateStrategy duplicateStrategy,
      List<ConsentImportMapping> consentMappings,
      List<ConsentImportDeclaration> consentDeclarations,
      String targetListId,
      String idempotencyKey) {}

  public record ImportErrorsResponse(
      long count, boolean ready, String downloadUrl, Instant expiresAt) {}

  public record ExportCreateRequest(
      SourceType sourceType, String sourceId, List<String> fields, String idempotencyKey) {}

  public record ContactExportResponse(
      String id,
      String status,
      JobResponse job,
      long rowCount,
      Instant expiresAt) {}

  public record ExportDownloadResponse(String url, Instant expiresAt) {}

  public enum SelectionType {
    IDS,
    LIST,
    SEGMENT,
    FILTER
  }

  public record ContactFilter(
      String q,
      String status,
      String tagId,
      String listId,
      Instant createdAfter,
      Instant createdBefore,
      String sort) {}

  public record BulkSelection(
      SelectionType type,
      List<String> ids,
      String listId,
      String segmentId,
      ContactFilter filter) {}

  public record BulkOperation(
      String type, String tagId, String listId, String fieldId, JsonNode value) {}

  public record BulkCreateRequest(
      BulkSelection selection, BulkOperation operation, String idempotencyKey) {}

  public record BulkOperationResponse(
      String id,
      String status,
      long candidateCount,
      long processedCount,
      long successCount,
      long errorCount,
      JobResponse job) {}
}

package com.messaging.contacts.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.contacts.processing.ContactExportRowSource.Source;
import com.messaging.contacts.processing.ContactProcessingApiModel.BulkOperation;
import com.messaging.contacts.processing.ContactProcessingApiModel.BulkOperationResponse;
import com.messaging.contacts.processing.ContactProcessingApiModel.BulkSelection;
import com.messaging.contacts.processing.ContactProcessingApiModel.ExportCreateRequest;
import com.messaging.contacts.processing.ContactProcessingApiModel.ContactExportResponse;
import com.messaging.contacts.processing.ContactProcessingApiModel.ContactImportResponse;
import com.messaging.contacts.processing.ContactProcessingApiModel.ExportDownloadResponse;
import com.messaging.contacts.processing.ContactProcessingApiModel.ImportErrorsResponse;
import com.messaging.contacts.processing.ContactProcessingApiModel.ImportPreviewResponse;
import com.messaging.contacts.processing.ContactProcessingApiModel.ImportUploadContract;
import com.messaging.contacts.processing.ContactProcessingApiModel.JobResponse;
import com.messaging.contacts.processing.ContactProcessingApiModel.SuggestedMapping;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContactProcessingApiMapper {

  private final ObjectMapper objectMapper;

  public Source exportSource(ExportCreateRequest request) {
    if (request == null || request.sourceType() == null) throw invalid();
    Long sourceId = parseOptionalId(request.sourceId());
    if (request.sourceType() == ContactExportRowSource.SourceType.CONTACTS) {
      if (sourceId != null) throw invalid();
    } else if (sourceId == null) {
      throw invalid();
    }
    return new Source(request.sourceType(), sourceId);
  }

  public ContactBulkService.Selection bulkSelection(BulkSelection selection) {
    if (selection == null || selection.type() == null) throw invalid();
    return switch (selection.type()) {
      case IDS ->
          new ContactBulkService.Selection(
              ContactBulkService.SelectionType.IDS,
              parseIds(selection.ids()),
              null,
              null);
      case LIST ->
          new ContactBulkService.Selection(
              ContactBulkService.SelectionType.LIST,
              null,
              requireId(selection.listId()),
              null);
      case SEGMENT ->
          new ContactBulkService.Selection(
              ContactBulkService.SelectionType.SEGMENT,
              null,
              requireId(selection.segmentId()),
              null);
      case FILTER -> {
        if (selection.filter() == null) throw invalid();
        yield new ContactBulkService.Selection(
            ContactBulkService.SelectionType.FILTER,
            null,
            null,
            new ContactBulkService.ContactFilter(
                selection.filter().q(),
                selection.filter().status(),
                parseOptionalId(selection.filter().tagId()),
                parseOptionalId(selection.filter().listId()),
                selection.filter().createdAfter(),
                selection.filter().createdBefore(),
                selection.filter().sort()));
      }
    };
  }

  public ContactBulkMutationPort.Operation bulkOperation(BulkOperation operation) {
    if (operation == null || operation.type() == null) throw invalid();
    try {
      return switch (operation.type()) {
        case "ADD_TAG", "REMOVE_TAG" ->
            new ContactBulkMutationPort.Operation(
                operation.type(), requireId(operation.tagId()), null, null);
        case "ADD_TO_LIST", "REMOVE_FROM_LIST" ->
            new ContactBulkMutationPort.Operation(
                operation.type(), requireId(operation.listId()), null, null);
        case "UPDATE_FIELD" ->
            new ContactBulkMutationPort.Operation(
                operation.type(),
                requireId(operation.fieldId()),
                null,
                operation.value() == null
                    ? "null"
                    : objectMapper.writeValueAsString(operation.value()));
        case "DELETE" ->
            new ContactBulkMutationPort.Operation(operation.type(), null, null, null);
        default -> throw invalid();
      };
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw invalid();
    }
  }

  public ContactImportResponse importIntent(ContactImportService.ImportIntent intent) {
    return new ContactImportResponse(
        intent.importId(),
        "DRAFT_UPLOAD",
        intent.fileName(),
        new ImportUploadContract(
            "PUT",
            intent.upload().uploadUrl(),
            intent.upload().requiredHeaders()),
        null,
        intent.maxBytes(),
        null,
        0,
        0,
        0,
        null,
        intent.createdAt(),
        intent.createdAt());
  }

  public ContactImportResponse importResource(ContactImportRepository.ImportRow row) {
    return new ContactImportResponse(
        Long.toString(row.id()),
        row.status(),
        row.originalFileName(),
        null,
        null,
        null,
        job(null, row.asyncJobId(), row.status(), row.processedRows(), row.succeededRows(), row.failedRows()),
        row.totalRows(),
        row.succeededRows(),
        row.failedRows(),
        id(row.targetListId()),
        row.createdAt(),
        latest(row.completedAt(), row.startedAt(), row.createdAt()));
  }

  public ImportPreviewResponse importPreview(ContactImportService.Preview preview) {
    return new ImportPreviewResponse(
        preview.headers(),
        preview.sampleRows(),
        ",",
        "UTF-8",
        null,
        List.of(),
        preview.headers().stream()
            .map(header -> new SuggestedMapping(header, suggestedTarget(header)))
            .filter(item -> item.target() != null)
            .toList());
  }

  public JobResponse importCommit(ContactImportService.CommitResult result) {
    ContactImportRepository.ImportRow row = result.importRow();
    return job(
        result.job(),
        row.asyncJobId(),
        row.status(),
        row.processedRows(),
        row.succeededRows(),
        row.failedRows());
  }

  public ImportErrorsResponse importErrors(ContactImportService.ErrorReport report) {
    return new ImportErrorsResponse(
        report.count(), report.ready(), report.downloadUrl(), report.expiresAt());
  }

  public ContactExportResponse exportResource(
      ContactExportRepository.ExportRow row, AsyncJobResponse job) {
    return new ContactExportResponse(
        Long.toString(row.id()),
        row.status(),
        job(job, row.asyncJobId(), row.status(), row.rowCount(), row.rowCount(), 0),
        row.rowCount(),
        row.expiresAt());
  }

  public ExportDownloadResponse exportDownload(ContactExportService.Download download) {
    return new ExportDownloadResponse(download.url(), download.expiresAt());
  }

  public JobResponse bulkJob(
      ContactBulkService.CreateResult result, ContactBulkRepository.BulkRow row) {
    return job(
        result.job(),
        row.asyncJobId(),
        row.status(),
        row.processedCount(),
        row.succeededCount(),
        row.failedCount());
  }

  public BulkOperationResponse bulkResource(ContactBulkRepository.BulkRow row) {
    return new BulkOperationResponse(
        Long.toString(row.id()),
        row.status(),
        row.candidateCount(),
        row.processedCount(),
        row.succeededCount(),
        row.failedCount(),
        job(
            null,
            row.asyncJobId(),
            row.status(),
            row.processedCount(),
            row.succeededCount(),
            row.failedCount()));
  }

  private List<Long> parseIds(List<String> values) {
    if (values == null || values.isEmpty() || values.size() > 500) throw invalid();
    return values.stream().map(this::requireId).distinct().toList();
  }

  private long requireId(String value) {
    Long parsed = parseOptionalId(value);
    if (parsed == null) throw invalid();
    return parsed;
  }

  public Long parseOptionalId(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      long parsed = Long.parseLong(value);
      if (parsed <= 0) throw invalid();
      return parsed;
    } catch (NumberFormatException exception) {
      throw invalid();
    }
  }

  private JobResponse job(
      AsyncJobResponse job,
      Long fallbackJobId,
      String fallbackStatus,
      long processed,
      long succeeded,
      long failed) {
    if (job != null) {
      return new JobResponse(
          job.jobId(),
          job.jobId(),
          job.status().name(),
          job.progressPercent(),
          job.completedUnits(),
          null,
          null,
          job.statusMessage(),
          job.createdAt(),
          job.updatedAt());
    }
    if (fallbackJobId == null) return null;
    return new JobResponse(
        Long.toString(fallbackJobId),
        Long.toString(fallbackJobId),
        jobStatus(fallbackStatus),
        null,
        processed,
        succeeded,
        failed,
        null,
        null,
        null);
  }

  private String jobStatus(String resourceStatus) {
    return switch (resourceStatus) {
      case "RUNNING" -> "RUNNING";
      case "COMPLETED" -> "COMPLETED";
      case "FAILED", "VALIDATION_FAILED", "EXPIRED" -> "FAILED";
      default -> "QUEUED";
    };
  }

  private String suggestedTarget(String header) {
    if (header == null) return null;
    return switch (header.trim().toLowerCase()) {
      case "email", "email address", "email_address" -> "IDENTITY:EMAIL";
      case "phone", "phone number", "mobile", "mobile number" -> "IDENTITY:PHONE";
      case "display name", "display_name", "name" -> "BUILTIN:displayName";
      case "first name", "first_name", "firstname" -> "BUILTIN:firstName";
      case "last name", "last_name", "lastname" -> "BUILTIN:lastName";
      case "organization", "company" -> "BUILTIN:organization";
      default -> null;
    };
  }

  private String id(Long value) {
    return value == null ? null : Long.toString(value);
  }

  private Instant latest(Instant first, Instant second, Instant third) {
    if (first != null) return first;
    if (second != null) return second;
    return third;
  }

  private com.messaging.common.exception.BadRequestException invalid() {
    return new com.messaging.common.exception.BadRequestException("CONTACT_FIELD_INVALID");
  }
}

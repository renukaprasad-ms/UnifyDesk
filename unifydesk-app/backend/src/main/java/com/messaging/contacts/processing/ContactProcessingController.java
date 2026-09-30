package com.messaging.contacts.processing;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.contacts.audience.ContactAudiencePermissions;
import com.messaging.contacts.processing.ContactExportRowSource.*;
import com.messaging.contacts.processing.ContactImportMutationPort.*;
import com.messaging.security.service.CurrentUserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/contacts")
@RequiredArgsConstructor
public class ContactProcessingController {

  private final ContactImportService importService;
  private final ContactExportService exportService;
  private final ContactBulkService bulkService;
  private final ContactActivityService activityService;
  private final ActivityApiMapper activityApiMapper;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  @PostMapping("/imports")
  public ResponseEntity<ApiResponse<ContactImportService.ImportIntent>> initiateImport(
      @PathVariable long companyId, @RequestBody ImportIntentRequest request) {
    long userId = require(companyId, ContactAudiencePermissions.IMPORT);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, importService.initiate(companyId, userId, request.sizeBytes()), "Import upload initiated"));
  }

  @PostMapping("/imports/{importId}/preview")
  public ResponseEntity<ApiResponse<ContactImportService.Preview>> previewImport(
      @PathVariable long companyId, @PathVariable long importId) {
    require(companyId, ContactAudiencePermissions.IMPORT);
    return ResponseEntity.ok(ApiResponse.success(200, importService.preview(companyId, importId), "Import preview"));
  }

  @PostMapping("/imports/{importId}/commit")
  public ResponseEntity<ApiResponse<ContactImportService.CommitResult>> commitImport(
      @PathVariable long companyId,
      @PathVariable long importId,
      @RequestBody ImportCommitRequest request) {
    long userId = require(companyId, ContactAudiencePermissions.IMPORT);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                202,
                importService.commit(
                    companyId,
                    userId,
                    importId,
                    request.mapping(),
                    request.duplicateStrategy(),
                    request.targetListId(),
                    request.idempotencyKey()),
                "Import queued"));
  }

  @GetMapping("/imports/{importId}")
  public ResponseEntity<ApiResponse<ContactImportRepository.ImportRow>> getImport(
      @PathVariable long companyId, @PathVariable long importId) {
    require(companyId, ContactAudiencePermissions.IMPORT);
    return ResponseEntity.ok(ApiResponse.success(200, importService.get(companyId, importId), "Import fetched"));
  }

  @GetMapping("/imports/{importId}/errors")
  public ResponseEntity<ApiResponse<ContactImportService.Download>> importErrors(
      @PathVariable long companyId, @PathVariable long importId) {
    require(companyId, ContactAudiencePermissions.IMPORT);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, importService.errors(companyId, importId), "Import error report ready"));
  }

  @PostMapping("/exports")
  public ResponseEntity<ApiResponse<ContactExportService.CreateResult>> createExport(
      @PathVariable long companyId, @RequestBody ExportRequest request) {
    long userId = require(companyId, ContactAudiencePermissions.EXPORT);
    Source source = new Source(request.sourceType(), request.sourceId());
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                202,
                exportService.create(companyId, userId, source, request.fields(), request.idempotencyKey()),
                "Export queued"));
  }

  @GetMapping("/exports/{exportId}")
  public ResponseEntity<ApiResponse<ContactExportRepository.ExportRow>> getExport(
      @PathVariable long companyId, @PathVariable long exportId) {
    require(companyId, ContactAudiencePermissions.EXPORT);
    return ResponseEntity.ok(ApiResponse.success(200, exportService.get(companyId, exportId), "Export fetched"));
  }

  @GetMapping("/exports/{exportId}/download")
  public ResponseEntity<ApiResponse<ContactExportService.Download>> downloadExport(
      @PathVariable long companyId, @PathVariable long exportId) {
    require(companyId, ContactAudiencePermissions.EXPORT);
    return ResponseEntity.ok(ApiResponse.success(200, exportService.download(companyId, exportId), "Export download ready"));
  }

  @PostMapping("/bulk-operations")
  public ResponseEntity<ApiResponse<ContactBulkService.CreateResult>> bulk(
      @PathVariable long companyId, @RequestBody BulkRequest request) {
    long userId = require(companyId, ContactAudiencePermissions.MODIFY);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(202, bulkService.create(companyId, userId, request.selection(), request.operation(), request.idempotencyKey()), "Bulk operation queued"));
  }

  @GetMapping("/{contactId}/activity")
  public ResponseEntity<ApiResponse<ActivityApiModel.ActivityPage>> activity(
      @PathVariable long companyId,
      @PathVariable long contactId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(required = false) List<String> types) {
    require(companyId, ContactAudiencePermissions.READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            activityApiMapper.toApi(
                activityService.page(companyId, contactId, cursor, size, types)),
            "Contact activity fetched"));
  }

  private long require(long companyId, String permission) {
    long userId = currentUser.currentUserId();
    authorization.requirePermission(userId, companyId, permission);
    return userId;
  }

  public record ImportIntentRequest(long sizeBytes) {}

  public record ImportCommitRequest(
      ImportMapping mapping,
      DuplicateStrategy duplicateStrategy,
      Long targetListId,
      String idempotencyKey) {}

  public record ExportRequest(
      SourceType sourceType, Long sourceId, List<String> fields, String idempotencyKey) {}

  public record BulkRequest(
      ContactBulkService.Selection selection,
      ContactBulkMutationPort.Operation operation,
      String idempotencyKey) {}
}

package com.messaging.contacts.processing;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.contacts.audience.ContactAudiencePermissions;
import com.messaging.contacts.processing.ContactProcessingApiModel.BulkCreateRequest;
import com.messaging.contacts.processing.ContactProcessingApiModel.BulkOperationResponse;
import com.messaging.contacts.processing.ContactProcessingApiModel.ContactExportResponse;
import com.messaging.contacts.processing.ContactProcessingApiModel.ContactImportResponse;
import com.messaging.contacts.processing.ContactProcessingApiModel.ExportCreateRequest;
import com.messaging.contacts.processing.ContactProcessingApiModel.ExportDownloadResponse;
import com.messaging.contacts.processing.ContactProcessingApiModel.ImportCommitRequest;
import com.messaging.contacts.processing.ContactProcessingApiModel.ImportCreateRequest;
import com.messaging.contacts.processing.ContactProcessingApiModel.ImportErrorsResponse;
import com.messaging.contacts.processing.ContactProcessingApiModel.ImportPreviewResponse;
import com.messaging.contacts.processing.ContactProcessingApiModel.JobResponse;
import com.messaging.security.service.CurrentUserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/contacts")
@RequiredArgsConstructor
public class ContactProcessingController {

  private final ContactImportService importService;
  private final ContactImportMappingMapper importMappingMapper;
  private final ContactExportService exportService;
  private final ContactBulkService bulkService;
  private final ContactBulkRepository bulkRepository;
  private final ContactActivityService activityService;
  private final ActivityApiMapper activityApiMapper;
  private final ContactProcessingApiMapper apiMapper;
  private final ContactProcessingAuthorizationPolicy processingAuthorization;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  @PostMapping("/imports")
  public ResponseEntity<ApiResponse<ContactImportResponse>> initiateImport(
      @PathVariable long companyId, @RequestBody ImportCreateRequest request) {
    long userId = require(companyId, ContactAudiencePermissions.IMPORT);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                apiMapper.importIntent(
                    importService.initiate(
                        companyId,
                        userId,
                        request.fileName(),
                        request.contentType(),
                        request.sizeBytes())),
                "Import upload initiated"));
  }

  @PostMapping("/imports/{importId}/preview")
  public ResponseEntity<ApiResponse<ImportPreviewResponse>> previewImport(
      @PathVariable long companyId, @PathVariable long importId) {
    require(companyId, ContactAudiencePermissions.IMPORT);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, apiMapper.importPreview(importService.preview(companyId, importId)), "Import preview"));
  }

  @PostMapping("/imports/{importId}/commit")
  public ResponseEntity<ApiResponse<JobResponse>> commitImport(
      @PathVariable long companyId,
      @PathVariable long importId,
      @RequestBody ImportCommitRequest request) {
    long userId = require(companyId, ContactAudiencePermissions.IMPORT);
    var mapping =
        importMappingMapper.toDomain(
            importService.headers(companyId, importId),
            request.mappings(),
            request.consentMappings(),
            request.consentDeclarations());
    processingAuthorization.requireImportCommit(userId, companyId, mapping);
    var result =
        importService.commit(
            companyId,
            userId,
            importId,
            mapping,
            request.duplicateStrategy(),
            apiMapper.parseOptionalId(request.targetListId()),
            request.idempotencyKey());
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(202, apiMapper.importCommit(result), "Import queued"));
  }

  @GetMapping("/imports/{importId}")
  public ResponseEntity<ApiResponse<ContactImportResponse>> getImport(
      @PathVariable long companyId, @PathVariable long importId) {
    require(companyId, ContactAudiencePermissions.IMPORT);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, apiMapper.importResource(importService.get(companyId, importId)), "Import fetched"));
  }

  @GetMapping("/imports/{importId}/errors")
  public ResponseEntity<ApiResponse<ImportErrorsResponse>> importErrors(
      @PathVariable long companyId, @PathVariable long importId) {
    require(companyId, ContactAudiencePermissions.IMPORT);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, apiMapper.importErrors(importService.errors(companyId, importId)), "Import errors"));
  }

  @PostMapping("/exports")
  public ResponseEntity<ApiResponse<ContactExportResponse>> createExport(
      @PathVariable long companyId, @RequestBody ExportCreateRequest request) {
    long userId = require(companyId, ContactAudiencePermissions.EXPORT);
    var result =
        exportService.create(
            companyId,
            userId,
            apiMapper.exportSource(request),
            request.fields(),
            request.idempotencyKey());
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                202,
                apiMapper.exportResource(result.export(), result.job()),
                "Export queued"));
  }

  @GetMapping("/exports/{exportId}")
  public ResponseEntity<ApiResponse<ContactExportResponse>> getExport(
      @PathVariable long companyId, @PathVariable long exportId) {
    require(companyId, ContactAudiencePermissions.EXPORT);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            apiMapper.exportResource(exportService.get(companyId, exportId), null),
            "Export fetched"));
  }

  @GetMapping("/exports/{exportId}/download")
  public ResponseEntity<ApiResponse<ExportDownloadResponse>> downloadExport(
      @PathVariable long companyId, @PathVariable long exportId) {
    require(companyId, ContactAudiencePermissions.EXPORT);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            apiMapper.exportDownload(exportService.download(companyId, exportId)),
            "Export download ready"));
  }

  @PostMapping("/bulk-operations")
  public ResponseEntity<ApiResponse<JobResponse>> bulk(
      @PathVariable long companyId, @RequestBody BulkCreateRequest request) {
    long userId = currentUser.currentUserId();
    ContactBulkMutationPort.Operation operation = apiMapper.bulkOperation(request.operation());
    processingAuthorization.requireBulkQueue(userId, companyId, operation);
    ContactBulkService.CreateResult result =
        bulkService.create(
            companyId,
            userId,
            apiMapper.bulkSelection(request.selection()),
            operation,
            request.idempotencyKey());
    ContactBulkRepository.BulkRow row =
        bulkRepository.require(companyId, result.operationId());
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(202, apiMapper.bulkJob(result, row), "Bulk operation queued"));
  }

  @GetMapping("/bulk-operations/{operationId}")
  public ResponseEntity<ApiResponse<BulkOperationResponse>> getBulk(
      @PathVariable long companyId, @PathVariable long operationId) {
    require(companyId, ContactAudiencePermissions.READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, apiMapper.bulkResource(bulkService.get(companyId, operationId)), "Bulk operation fetched"));
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
}

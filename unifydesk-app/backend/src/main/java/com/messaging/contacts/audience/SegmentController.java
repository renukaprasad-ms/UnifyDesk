package com.messaging.contacts.audience;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/contacts/segments")
@RequiredArgsConstructor
public class SegmentController {

  private final SegmentService service;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<SegmentService.SegmentResponse>>> list(
      @PathVariable long companyId) {
    require(companyId, ContactAudiencePermissions.READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.list(companyId), "Segments fetched"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<SegmentService.SegmentResponse>> create(
      @PathVariable long companyId, @RequestBody UpsertSegmentRequest request) {
    long userId = require(companyId, ContactAudiencePermissions.MODIFY);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                service.create(companyId, userId, request.name(), request.description(), request.filter()),
                "Segment created"));
  }

  @GetMapping("/{segmentId}")
  public ResponseEntity<ApiResponse<SegmentService.SegmentResponse>> get(
      @PathVariable long companyId, @PathVariable long segmentId) {
    require(companyId, ContactAudiencePermissions.READ);
    return ResponseEntity.ok(ApiResponse.success(200, service.get(companyId, segmentId), "Segment fetched"));
  }

  @PutMapping("/{segmentId}")
  public ResponseEntity<ApiResponse<SegmentService.SegmentResponse>> update(
      @PathVariable long companyId,
      @PathVariable long segmentId,
      @RequestBody UpsertSegmentRequest request) {
    require(companyId, ContactAudiencePermissions.MODIFY);
    if (request.version() == null) {
      throw new com.messaging.common.exception.BadRequestException("CONTACT_VERSION_CONFLICT");
    }
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            service.update(
                companyId,
                segmentId,
                request.version(),
                request.name(),
                request.description(),
                request.filter()),
            "Segment updated"));
  }

  @DeleteMapping("/{segmentId}")
  public ResponseEntity<ApiResponse<Void>> archive(
      @PathVariable long companyId, @PathVariable long segmentId) {
    require(companyId, ContactAudiencePermissions.MODIFY);
    service.archive(companyId, segmentId);
    return ResponseEntity.ok(ApiResponse.success(200, null, "Segment archived"));
  }

  @PostMapping("/validate")
  public ResponseEntity<ApiResponse<SegmentService.ValidationResult>> validate(
      @PathVariable long companyId, @RequestBody FilterRequest request) {
    require(companyId, ContactAudiencePermissions.READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, service.validate(companyId, request.filter()), "Segment filter validated"));
  }

  @PostMapping("/preview")
  public ResponseEntity<ApiResponse<SegmentService.PreviewResult>> preview(
      @PathVariable long companyId, @RequestBody FilterRequest request) {
    require(companyId, ContactAudiencePermissions.READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.preview(companyId, request.filter(), request.sampleSize()), "Segment preview"));
  }

  @PostMapping("/{segmentId}/refresh-count")
  public ResponseEntity<ApiResponse<AsyncJobResponse>> refreshCount(
      @PathVariable long companyId, @PathVariable long segmentId) {
    long userId = require(companyId, ContactAudiencePermissions.READ);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                202, service.refreshCount(companyId, userId, segmentId), "Segment count queued"));
  }

  private long require(long companyId, String permission) {
    long userId = currentUserService.currentUserId();
    authorizationService.requirePermission(userId, companyId, permission);
    return userId;
  }

  public record UpsertSegmentRequest(
      String name, String description, SegmentFilter filter, Long version) {}

  public record FilterRequest(SegmentFilter filter, Integer sampleSize) {}
}

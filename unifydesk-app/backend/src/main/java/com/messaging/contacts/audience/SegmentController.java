package com.messaging.contacts.audience;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.response.ApiResponse;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.contacts.audience.SegmentApiModel.FilterRequest;
import com.messaging.contacts.audience.SegmentApiModel.PreviewResponse;
import com.messaging.contacts.audience.SegmentApiModel.SegmentResponse;
import com.messaging.contacts.audience.SegmentApiModel.UpsertRequest;
import com.messaging.contacts.audience.SegmentApiModel.ValidationResponse;
import com.messaging.security.service.CurrentUserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/contacts/segments")
@RequiredArgsConstructor
public class SegmentController {

  private final SegmentService service;
  private final SegmentApiMapper mapper;
  private final CompanyAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<SegmentResponse>>> list(@PathVariable long companyId) {
    require(companyId, ContactAudiencePermissions.READ);
    return ResponseEntity.ok(
        ApiResponse.success(
            200, service.list(companyId).stream().map(mapper::toApi).toList(), "Segments fetched"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<SegmentResponse>> create(
      @PathVariable long companyId, @RequestBody UpsertRequest request) {
    long userId = require(companyId, ContactAudiencePermissions.MODIFY);
    SegmentFilter filter = mapper.toDomain(companyId, request.filterVersion(), request.filter());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                mapper.toApi(
                    service.create(
                        companyId, userId, request.name(), request.description(), filter)),
                "Segment created"));
  }

  @GetMapping("/{segmentId}")
  public ResponseEntity<ApiResponse<SegmentResponse>> get(
      @PathVariable long companyId, @PathVariable long segmentId) {
    require(companyId, ContactAudiencePermissions.READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, mapper.toApi(service.get(companyId, segmentId)), "Segment fetched"));
  }

  @PutMapping("/{segmentId}")
  public ResponseEntity<ApiResponse<SegmentResponse>> update(
      @PathVariable long companyId,
      @PathVariable long segmentId,
      @RequestBody UpsertRequest request) {
    require(companyId, ContactAudiencePermissions.MODIFY);
    if (request.version() == null) {
      throw new BadRequestException("CONTACT_VERSION_CONFLICT");
    }
    SegmentFilter filter = mapper.toDomain(companyId, request.filterVersion(), request.filter());
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            mapper.toApi(
                service.update(
                    companyId,
                    segmentId,
                    request.version(),
                    request.name(),
                    request.description(),
                    filter)),
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
  public ResponseEntity<ApiResponse<ValidationResponse>> validate(
      @PathVariable long companyId, @RequestBody FilterRequest request) {
    require(companyId, ContactAudiencePermissions.READ);
    try {
      SegmentFilter filter =
          mapper.toDomain(companyId, request.filterVersion(), request.filter());
      return ResponseEntity.ok(
          ApiResponse.success(
              200, mapper.valid(service.validate(companyId, filter)), "Segment filter validated"));
    } catch (BadRequestException exception) {
      return ResponseEntity.ok(
          ApiResponse.success(200, mapper.invalidValidation(), "Segment filter invalid"));
    }
  }

  @PostMapping("/preview")
  public ResponseEntity<ApiResponse<PreviewResponse>> preview(
      @PathVariable long companyId, @RequestBody FilterRequest request) {
    require(companyId, ContactAudiencePermissions.READ);
    SegmentFilter filter = mapper.toDomain(companyId, request.filterVersion(), request.filter());
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            mapper.toApi(service.preview(companyId, filter, request.sampleSize())),
            "Segment preview"));
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
}

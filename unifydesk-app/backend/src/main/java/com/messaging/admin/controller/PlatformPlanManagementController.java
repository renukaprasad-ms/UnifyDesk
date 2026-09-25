package com.messaging.admin.controller;

import com.messaging.admin.dto.PlatformPlanRequest;
import com.messaging.admin.dto.PlatformPlanResponse;
import com.messaging.admin.service.PlatformPlanManagementService;
import com.messaging.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/plans")
@RequiredArgsConstructor
public class PlatformPlanManagementController {
  private final PlatformPlanManagementService service;

  @GetMapping
  public ResponseEntity<ApiResponse<Page<PlatformPlanResponse>>> list(
      @RequestParam(defaultValue = "") String search,
      @PageableDefault(size = 20, sort = "name") Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(200, service.list(search, pageable), "Plans fetched"));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<PlatformPlanResponse>> get(@PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.success(200, service.get(id), "Plan fetched"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<PlatformPlanResponse>> create(
      @Valid @RequestBody PlatformPlanRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, service.create(request), "Plan created"));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<PlatformPlanResponse>> update(
      @PathVariable Long id, @Valid @RequestBody PlatformPlanRequest request) {
    return ResponseEntity.ok(ApiResponse.success(200, service.update(id, request), "Plan updated"));
  }

  @PostMapping("/{id}/publish")
  public ResponseEntity<ApiResponse<PlatformPlanResponse>> publish(@PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.success(200, service.publish(id), "Plan published"));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.archive(id);
    return ResponseEntity.noContent().build();
  }
}

package com.messaging.admin.controller;

import com.messaging.admin.dto.*;
import com.messaging.admin.service.PlatformPlanManagementService;
import com.messaging.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
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
      @Valid @RequestBody PlatformPlanRequest r) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, service.create(r), "Plan created"));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<PlatformPlanResponse>> update(
      @PathVariable Long id, @Valid @RequestBody PlatformPlanRequest r) {
    return ResponseEntity.ok(ApiResponse.success(200, service.update(id, r), "Plan updated"));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.archive(id);
    return ResponseEntity.noContent().build();
  }
}

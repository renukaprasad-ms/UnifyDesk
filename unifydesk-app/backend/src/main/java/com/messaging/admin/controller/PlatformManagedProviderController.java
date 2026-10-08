package com.messaging.admin.controller;

import com.messaging.admin.service.PlatformManagedProviderService;
import com.messaging.admin.service.PlatformManagedProviderService.Route;
import com.messaging.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/managed-providers")
@RequiredArgsConstructor
public class PlatformManagedProviderController {
  private final PlatformManagedProviderService service;

  public record ChangeRequest(
      @NotBlank String provider,
      @Min(1) long expectedRevision,
      @NotBlank @Size(min=8,max=500) String reason) {}

  @GetMapping
  public ResponseEntity<ApiResponse<List<Route>>> list() {
    return ResponseEntity.ok(ApiResponse.success(200,service.all(),"Provider routing fetched"));
  }

  @PutMapping("/{channel}")
  public ResponseEntity<ApiResponse<Route>> update(
      @PathVariable String channel, @Valid @RequestBody ChangeRequest request) {
    return ResponseEntity.ok(ApiResponse.success(
        200,service.change(channel,request.provider(),request.expectedRevision(),request.reason()),
        "Provider routing updated for future managed sends"));
  }
}

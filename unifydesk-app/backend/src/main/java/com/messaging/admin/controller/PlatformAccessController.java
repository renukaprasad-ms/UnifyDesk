package com.messaging.admin.controller;

import com.messaging.admin.dto.PlatformAccessResponse;
import com.messaging.admin.service.PlatformAccessService;
import com.messaging.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class PlatformAccessController {
  private final PlatformAccessService service;

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<PlatformAccessResponse>> me() {
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), service.me(), "Platform access fetched"));
  }
}

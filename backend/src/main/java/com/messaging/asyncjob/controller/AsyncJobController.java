package com.messaging.asyncjob.controller;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.common.response.ApiResponse;
import com.messaging.security.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class AsyncJobController {

  private final AsyncJobService asyncJobService;
  private final CurrentUserService currentUserService;

  @GetMapping("/{jobId}")
  public ResponseEntity<ApiResponse<AsyncJobResponse>> get(@PathVariable Long jobId) {
    Long userId = currentUserService.currentUserId();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            asyncJobService.getForUser(jobId, userId),
            "Async job fetched"));
  }
}

package com.messaging.media.controller;

import com.messaging.common.response.ApiResponse;
import com.messaging.media.dto.InitiateMediaUploadRequest;
import com.messaging.media.dto.InitiateMediaUploadResponse;
import com.messaging.media.dto.MediaAccessResponse;
import com.messaging.media.dto.MediaResponse;
import com.messaging.media.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

  private final MediaService mediaService;

  @PostMapping("/uploads")
  public ResponseEntity<ApiResponse<InitiateMediaUploadResponse>> initiateUpload(
      @Valid @RequestBody InitiateMediaUploadRequest request) {
    InitiateMediaUploadResponse response = mediaService.initiateUpload(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response, "Media upload initiated"));
  }

  @PostMapping("/{mediaId}/complete")
  public ResponseEntity<ApiResponse<MediaResponse>> completeUpload(@PathVariable String mediaId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), mediaService.completeUpload(mediaId), "Media upload completed"));
  }

  @GetMapping("/{mediaId}/access")
  public ResponseEntity<ApiResponse<MediaAccessResponse>> access(@PathVariable String mediaId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), mediaService.createAccessUrl(mediaId), "Media access granted"));
  }

  @DeleteMapping("/{mediaId}")
  public ResponseEntity<ApiResponse<MediaResponse>> delete(@PathVariable String mediaId) {
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), mediaService.delete(mediaId), "Media deleted"));
  }
}

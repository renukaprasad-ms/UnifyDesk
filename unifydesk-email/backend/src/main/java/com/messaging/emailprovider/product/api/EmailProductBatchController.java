package com.messaging.emailprovider.product.api;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.product.auth.EmailProductKeyFilter;
import com.messaging.emailprovider.product.service.EmailBatchService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/v1/email-batches")
public class EmailProductBatchController {
  private final EmailBatchService batches;

  public EmailProductBatchController(EmailBatchService batches) {
    this.batches = batches;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<BulkEmailResponses.Batch>> submit(
      HttpServletRequest context,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody BulkEmailRequests.NewBatch request) {
    Long projectId = EmailProductKeyFilter.require(context).projectId();
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(202, batches.submit(projectId, key, request),
            "Batch accepted for asynchronous delivery"));
  }

  @GetMapping("/{batchId}")
  public ApiResponse<BulkEmailResponses.Batch> get(
      HttpServletRequest context, @PathVariable @Positive Long batchId) {
    Long projectId = EmailProductKeyFilter.require(context).projectId();
    return ApiResponse.success(200, batches.get(projectId, batchId), "Email batch");
  }
}

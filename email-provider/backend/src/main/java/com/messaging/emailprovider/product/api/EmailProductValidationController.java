package com.messaging.emailprovider.product.api;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.product.auth.EmailProductKeyFilter;
import com.messaging.emailprovider.validation.dto.CreateValidationJobRequest;
import com.messaging.emailprovider.validation.dto.ValidationJobResponse;
import com.messaging.emailprovider.validation.dto.ValidationResultsResponse;
import com.messaging.emailprovider.validation.service.ValidationJobService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/v1/validations")
public class EmailProductValidationController {
  private final ValidationJobService validation;

  public EmailProductValidationController(ValidationJobService validation) {
    this.validation = validation;
  }

  @PostMapping("/jobs")
  public ResponseEntity<ApiResponse<ValidationJobResponse>> create(
      HttpServletRequest context,
      @Valid @RequestBody CreateValidationJobRequest request) {
    if (request.emails().size() > 1000) {
      throw new BadRequestException("Maximum 1000 addresses per public validation job");
    }
    Long projectId = EmailProductKeyFilter.require(context).projectId();
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(202, validation.create(projectId, request),
            "Validation queued"));
  }

  @GetMapping("/jobs/{jobId}")
  public ApiResponse<ValidationJobResponse> get(
      HttpServletRequest context, @PathVariable @Positive Long jobId) {
    Long projectId = EmailProductKeyFilter.require(context).projectId();
    return ApiResponse.success(200, validation.get(projectId, jobId), "Validation job");
  }

  @GetMapping("/jobs/{jobId}/results")
  public ApiResponse<ValidationResultsResponse> results(
      HttpServletRequest context, @PathVariable @Positive Long jobId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size) {
    Long projectId = EmailProductKeyFilter.require(context).projectId();
    return ApiResponse.success(200, validation.results(projectId, jobId, page, size),
        "Validation results");
  }
}

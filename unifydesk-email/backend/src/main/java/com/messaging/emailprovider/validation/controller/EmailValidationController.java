package com.messaging.emailprovider.validation.controller;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.validation.dto.CreateValidationJobRequest;
import com.messaging.emailprovider.validation.dto.SingleValidationRequest;
import com.messaging.emailprovider.validation.dto.ValidationJobResponse;
import com.messaging.emailprovider.validation.dto.ValidationResultsResponse;
import com.messaging.emailprovider.validation.service.ValidationJobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/companies/{companyId}/email-validation")
public class EmailValidationController {

  private final ValidationJobService jobs;

  public EmailValidationController(ValidationJobService jobs) {
    this.jobs = jobs;
  }

  @PostMapping("/jobs")
  public ResponseEntity<ApiResponse<ValidationJobResponse>> create(
      @PathVariable @Positive Long companyId,
      @Valid @RequestBody CreateValidationJobRequest request) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                HttpStatus.ACCEPTED.value(),
                jobs.create(companyId, request),
                "Email validation job accepted"));
  }

  @PostMapping("/single")
  public ResponseEntity<ApiResponse<ValidationJobResponse>> single(
      @PathVariable @Positive Long companyId, @Valid @RequestBody SingleValidationRequest request) {
    CreateValidationJobRequest jobRequest =
        new CreateValidationJobRequest(List.of(request.email()));
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(
                HttpStatus.ACCEPTED.value(),
                jobs.create(companyId, jobRequest),
                "Email validation accepted"));
  }

  @GetMapping("/jobs/{jobId}")
  public ApiResponse<ValidationJobResponse> get(
      @PathVariable @Positive Long companyId, @PathVariable @Positive Long jobId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), jobs.get(companyId, jobId), "Email validation job");
  }

  @GetMapping("/jobs/{jobId}/results")
  public ApiResponse<ValidationResultsResponse> results(
      @PathVariable @Positive Long companyId,
      @PathVariable @Positive Long jobId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        jobs.results(companyId, jobId, page, size),
        "Email validation results");
  }
}

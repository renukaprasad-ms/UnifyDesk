package com.messaging.emailprovider.domain.controller;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.domain.dto.CreateSendingDomainRequest;
import com.messaging.emailprovider.domain.dto.SendingDomainResponse;
import com.messaging.emailprovider.domain.service.SendingDomainService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/companies/{companyId}/domains")
public class SendingDomainController {

  private final SendingDomainService service;

  public SendingDomainController(SendingDomainService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<SendingDomainResponse>> create(
      @PathVariable @Positive Long companyId,
      @Valid @RequestBody CreateSendingDomainRequest request) {
    SendingDomainResponse response = service.create(companyId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response, "Sending domain created"));
  }

  @GetMapping
  public ApiResponse<List<SendingDomainResponse>> list(@PathVariable @Positive Long companyId) {
    return ApiResponse.success(HttpStatus.OK.value(), service.list(companyId), "Sending domains");
  }

  @GetMapping("/{domainId}")
  public ApiResponse<SendingDomainResponse> get(
      @PathVariable @Positive Long companyId, @PathVariable @Positive Long domainId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), service.get(companyId, domainId), "Sending domain");
  }

  @PostMapping("/{domainId}/verify")
  public ApiResponse<SendingDomainResponse> verify(
      @PathVariable @Positive Long companyId, @PathVariable @Positive Long domainId) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        service.verify(companyId, domainId),
        "Domain verification completed");
  }

  @DeleteMapping("/{domainId}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable @Positive Long companyId, @PathVariable @Positive Long domainId) {
    service.delete(companyId, domainId);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Sending domain deleted"));
  }
}

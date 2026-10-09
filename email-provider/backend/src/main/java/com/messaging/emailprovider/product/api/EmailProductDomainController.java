package com.messaging.emailprovider.product.api;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.domain.dto.CreateSendingDomainRequest;
import com.messaging.emailprovider.domain.dto.SendingDomainResponse;
import com.messaging.emailprovider.domain.service.SendingDomainService;
import com.messaging.emailprovider.product.auth.EmailProductKeyFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/v1/domains")
public class EmailProductDomainController {
  private final SendingDomainService domains;

  public EmailProductDomainController(SendingDomainService domains) {
    this.domains = domains;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<SendingDomainResponse>> create(
      HttpServletRequest context, @Valid @RequestBody CreateSendingDomainRequest request) {
    Long projectId = EmailProductKeyFilter.require(context).projectId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, domains.create(projectId, request), "Domain created"));
  }

  @GetMapping
  public ApiResponse<List<SendingDomainResponse>> list(HttpServletRequest context) {
    Long projectId = EmailProductKeyFilter.require(context).projectId();
    return ApiResponse.success(200, domains.list(projectId), "Domains");
  }

  @GetMapping("/{domainId}")
  public ApiResponse<SendingDomainResponse> get(
      HttpServletRequest context, @PathVariable @Positive Long domainId) {
    Long projectId = EmailProductKeyFilter.require(context).projectId();
    return ApiResponse.success(200, domains.get(projectId, domainId), "Domain");
  }

  @PostMapping("/{domainId}/verify")
  public ApiResponse<SendingDomainResponse> verify(
      HttpServletRequest context, @PathVariable @Positive Long domainId) {
    Long projectId = EmailProductKeyFilter.require(context).projectId();
    return ApiResponse.success(200, domains.verify(projectId, domainId), "Domain verification");
  }
}

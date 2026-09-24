package com.messaging.emailprovider.suppression.controller;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.suppression.SuppressionService;
import com.messaging.emailprovider.suppression.dto.AddSuppressionRequest;
import com.messaging.emailprovider.suppression.dto.SuppressionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@Validated
@RestController
@RequestMapping("/api/v1/companies/{companyId}/suppressions")
public class SuppressionController {

  private final SuppressionService service;

  public SuppressionController(SuppressionService service) {
    this.service = service;
  }

  @PostMapping
  public ApiResponse<SuppressionResponse> add(
      @PathVariable @Positive Long companyId, @Valid @RequestBody AddSuppressionRequest request) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        SuppressionResponse.from(
            service.add(companyId, request.email(), request.reason(), request.expiresAt())),
        "Suppression created");
  }

  @GetMapping
  public ApiResponse<List<SuppressionResponse>> list(@PathVariable @Positive Long companyId) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        service.list(companyId).stream().map(SuppressionResponse::from).toList(),
        "Suppressions");
  }

  @DeleteMapping("/{suppressionId}")
  public ApiResponse<Void> remove(
      @PathVariable @Positive Long companyId, @PathVariable @Positive Long suppressionId) {
    service.remove(companyId, suppressionId);
    return ApiResponse.success(HttpStatus.OK.value(), "Suppression removed");
  }
}

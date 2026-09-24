package com.messaging.emailprovider.reputation.controller;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.reputation.ReputationQueryService;
import com.messaging.emailprovider.reputation.dto.ReputationDailyResponse;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/companies/{companyId}/reputation")
public class ReputationController {

  private final ReputationQueryService service;

  public ReputationController(ReputationQueryService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<ReputationDailyResponse>> query(
      @PathVariable @Positive Long companyId,
      @RequestParam(required = false) @Positive Long sendingDomainId,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    LocalDate resolvedTo = to == null ? LocalDate.now() : to;
    LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(29) : from;
    return ApiResponse.success(
        HttpStatus.OK.value(),
        service.query(companyId, sendingDomainId, resolvedFrom, resolvedTo),
        "Email reputation");
  }
}

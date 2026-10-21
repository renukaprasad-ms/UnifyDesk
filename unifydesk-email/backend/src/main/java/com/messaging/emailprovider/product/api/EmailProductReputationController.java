package com.messaging.emailprovider.product.api;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.product.auth.EmailProductKeyFilter;
import com.messaging.emailprovider.reputation.ReputationQueryService;
import com.messaging.emailprovider.reputation.dto.ReputationDailyResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/v1/reputation")
public class EmailProductReputationController {
  private final ReputationQueryService reputation;

  public EmailProductReputationController(ReputationQueryService reputation) {
    this.reputation = reputation;
  }

  @GetMapping
  public ApiResponse<List<ReputationDailyResponse>> get(
      HttpServletRequest context,
      @RequestParam(required = false) @Positive Long sendingDomainId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    Long projectId = EmailProductKeyFilter.require(context).projectId();
    LocalDate last = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
    LocalDate first = from == null ? last.minusDays(29) : from;
    return ApiResponse.success(
        200, reputation.query(projectId, sendingDomainId, first, last), "Project reputation");
  }
}

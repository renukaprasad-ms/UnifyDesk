package com.messaging.emailprovider.product.usage;

import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.product.auth.EmailProductKeyFilter;
import com.messaging.emailprovider.product.console.ConsoleAuthFilter;
import com.messaging.emailprovider.product.model.EmailProject;
import com.messaging.emailprovider.product.repo.EmailProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProductUsageController {
  private final ProductUsageService usage;
  private final EmailProjectRepository projects;

  public ProductUsageController(ProductUsageService usage, EmailProjectRepository projects) {
    this.usage = usage;
    this.projects = projects;
  }

  @GetMapping("/v1/usage")
  public ApiResponse<ProductUsageService.Usage> api(
      HttpServletRequest context,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ApiResponse.success(
        200, report(EmailProductKeyFilter.require(context).projectId(), from, to), "Usage ledger");
  }

  @GetMapping("/console/v1/projects/{projectId}/usage")
  public ApiResponse<ProductUsageService.Usage> console(
      HttpServletRequest context,
      @PathVariable @Positive Long projectId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    var user = ConsoleAuthFilter.require(context);
    EmailProject project =
        projects.findById(projectId).orElseThrow(() -> new NotFoundException("Project not found"));
    if (!user.organizationId().equals(project.getOrganizationId())) {
      throw new NotFoundException("Project not found");
    }
    return ApiResponse.success(200, report(projectId, from, to), "Usage ledger");
  }

  private ProductUsageService.Usage report(Long projectId, LocalDate from, LocalDate to) {
    LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
    return usage.report(projectId, from == null ? end.minusDays(29) : from, end);
  }
}

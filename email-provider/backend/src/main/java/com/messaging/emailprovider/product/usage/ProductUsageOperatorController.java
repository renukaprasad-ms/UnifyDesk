package com.messaging.emailprovider.product.usage;

import com.messaging.emailprovider.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/email-product/projects/{projectId}/billing")
public class ProductUsageOperatorController {
  private final ProductUsageService usage;
  public ProductUsageOperatorController(ProductUsageService usage) {
    this.usage = usage;
  }

  public record RateChange(@NotNull @Min(0) Long unitPriceMicroInr) {}

  /**
   * Protected by InternalApiTokenFilter. Explicit project-scoping is mandatory because
   * independent Email product project IDs are not UnifyDesk company IDs.
   */
  @GetMapping("/usage-head")
  public ApiResponse<String> usageHead(@PathVariable @Positive Long projectId) {
    return ApiResponse.success(200, usage.latestUsageId(projectId), "Current handoff high-water mark");
  }

  @GetMapping("/usage-entries")
  public ApiResponse<ProductUsageService.HandoffPage> usageEntries(
      @PathVariable @Positive Long projectId,
      @RequestParam(defaultValue = "0") @Min(0) long afterId,
      @RequestParam(defaultValue = "100") @Min(1) int limit) {
    return ApiResponse.success(200, usage.handoffs(projectId, afterId, limit),
        "Immutable recipient handoffs");
  }

  @PostMapping("/rate")
  public ApiResponse<Void> setRate(@PathVariable @Positive Long projectId,
      @Valid @RequestBody RateChange change) {
    usage.setRate(projectId, change.unitPriceMicroInr());
    return ApiResponse.success(200, "Future handoffs use updated rate");
  }
}

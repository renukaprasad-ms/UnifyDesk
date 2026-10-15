package com.messaging.dashboard;

import com.messaging.common.response.ApiResponse;
import com.messaging.security.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {
  private final DashboardService dashboardService;
  private final CurrentUserService currentUserService;

  @GetMapping
  public ApiResponse<DashboardResponse> get(@PathVariable Long companyId) {
    return ApiResponse.success(
        200,
        dashboardService.get(companyId, currentUserService.currentUserId()),
        "Dashboard fetched");
  }

  @GetMapping("/core")
  public ApiResponse<DashboardResponse> core(@PathVariable Long companyId) {
    return section(companyId, DashboardService.Section.CORE);
  }

  @GetMapping("/channels")
  public ApiResponse<DashboardResponse> channels(@PathVariable Long companyId) {
    return section(companyId, DashboardService.Section.CHANNELS);
  }

  @GetMapping("/ads")
  public ApiResponse<DashboardResponse> ads(@PathVariable Long companyId) {
    return section(companyId, DashboardService.Section.ADS);
  }

  private ApiResponse<DashboardResponse> section(Long companyId, DashboardService.Section section) {
    return ApiResponse.success(
        200,
        dashboardService.get(companyId, currentUserService.currentUserId(), section),
        "Dashboard section fetched");
  }
}

package com.messaging.bootstrap;

import com.messaging.admin.service.SystemSuperAdminBootstrapService;
import com.messaging.billing.bootstrap.SystemBillingBootstrapService;
import com.messaging.company.service.SystemPermissionCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class SystemPlatformBootstrapInitializer implements ApplicationRunner {

  private final SystemPermissionCatalogService permissionCatalogService;
  private final SystemBillingBootstrapService billingBootstrapService;
  private final SystemSuperAdminBootstrapService superAdminBootstrapService;

  @Override
  public void run(ApplicationArguments args) {
    int restoredPermissions = permissionCatalogService.ensureCatalog();
    Long freePlanId = billingBootstrapService.ensureFreePlan();
    Long superAdminUserId = superAdminBootstrapService.ensureSuperAdmin();

    log.info(
        "system_platform_bootstrap_ready freePlanId={} superAdminUserId={} restoredPermissions={}",
        freePlanId,
        superAdminUserId,
        restoredPermissions);
  }
}

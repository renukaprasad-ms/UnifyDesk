package com.messaging.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.company.enums.CompanyRole;
import com.messaging.company.iam.CompanyPermissionKeys;
import org.junit.jupiter.api.Test;

class CrmPermissionDefaultsTests {

  @Test
  void managersCanReadAndModifyCrmResources() {
    assertThat(CompanyPermissionKeys.defaultsFor(CompanyRole.MANAGER))
        .contains(
            CompanyPermissionKeys.CRM_LEAD_READ,
            CompanyPermissionKeys.CRM_LEAD_MODIFY,
            CompanyPermissionKeys.CRM_ACCOUNT_READ,
            CompanyPermissionKeys.CRM_ACCOUNT_MODIFY,
            CompanyPermissionKeys.CRM_TASK_READ,
            CompanyPermissionKeys.CRM_TASK_MODIFY);
  }

  @Test
  void membersCanReadButCannotModifyCrmResources() {
    assertThat(CompanyPermissionKeys.defaultsFor(CompanyRole.MEMBER))
        .contains(
            CompanyPermissionKeys.CRM_LEAD_READ,
            CompanyPermissionKeys.CRM_ACCOUNT_READ,
            CompanyPermissionKeys.CRM_TASK_READ)
        .doesNotContain(
            CompanyPermissionKeys.CRM_LEAD_MODIFY,
            CompanyPermissionKeys.CRM_ACCOUNT_MODIFY,
            CompanyPermissionKeys.CRM_TASK_MODIFY);
  }

  @Test
  void ownerRoleUsesCatalogRatherThanAStaticGrantList() {
    assertThat(CompanyPermissionKeys.defaultsFor(CompanyRole.OWNER)).isEmpty();
  }
}

package com.messaging.contacts.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.company.enums.CompanyRole;
import com.messaging.company.iam.CompanyPermissionKeys;
import org.junit.jupiter.api.Test;

class ContactPermissionDefaultsTests {

  @Test
  void managerGetsOperationalContactPermissionsButNotDestructiveDelete() {
    assertThat(CompanyPermissionKeys.defaultsFor(CompanyRole.MANAGER))
        .contains(
            CompanyPermissionKeys.CONTACT_READ,
            CompanyPermissionKeys.CONTACT_CREATE,
            CompanyPermissionKeys.CONTACT_MODIFY,
            CompanyPermissionKeys.CONTACT_IMPORT,
            CompanyPermissionKeys.CONTACT_EXPORT,
            CompanyPermissionKeys.CONTACT_CONSENT_READ,
            CompanyPermissionKeys.CONTACT_CONSENT_MODIFY)
        .doesNotContain(CompanyPermissionKeys.CONTACT_DELETE);
  }

  @Test
  void memberContactDefaultsAreReadAndConsentReadOnly() {
    var permissions = CompanyPermissionKeys.defaultsFor(CompanyRole.MEMBER);

    assertThat(permissions)
        .contains(
            CompanyPermissionKeys.CONTACT_READ,
            CompanyPermissionKeys.CONTACT_CONSENT_READ)
        .doesNotContain(
            CompanyPermissionKeys.CONTACT_CREATE,
            CompanyPermissionKeys.CONTACT_MODIFY,
            CompanyPermissionKeys.CONTACT_DELETE,
            CompanyPermissionKeys.CONTACT_IMPORT,
            CompanyPermissionKeys.CONTACT_EXPORT,
            CompanyPermissionKeys.CONTACT_CONSENT_MODIFY);
  }
}

package com.messaging.emailprovider.validation.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoleAddressPolicyTests {

  private final RoleAddressPolicy policy = new RoleAddressPolicy();

  @Test
  void detectsRoleAccountsCaseInsensitively() {
    assertThat(policy.isRoleAddress("Support")).isTrue();
    assertThat(policy.isRoleAddress("postmaster")).isTrue();
    assertThat(policy.isRoleAddress("renuka")).isFalse();
  }
}

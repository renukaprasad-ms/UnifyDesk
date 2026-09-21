package com.messaging.redis.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RedisKeyTests {

  @Test
  void createsNamespacedKeys() {
    assertThat(RedisKey.authOtp("USER@Example.COM"))
        .isEqualTo("messaging:auth:otp:user@example.com");
    assertThat(RedisKey.companySettings("Company-1"))
        .isEqualTo("messaging:company:company-1:settings");
    assertThat(RedisKey.idempotency("Webhook", "Request-1"))
        .isEqualTo("messaging:idempotency:webhook:request-1");
    assertThat(RedisKey.gmailQuotaUserTotal("User@Example.COM"))
        .isEqualTo("messaging:gmail:quota:user:user@example.com:total");
    assertThat(RedisKey.gmailQuotaCooldown("User@Example.COM"))
        .isEqualTo("messaging:gmail:quota:cooldown:user@example.com");
  }

  @Test
  void rejectsBlankParts() {
    assertThatThrownBy(() -> RedisKey.authOtp(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }
}

package com.messaging.managedsms.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class ManagedSmsRateLimiterTests {

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void evaluatesAllDistributedScopesAtomically() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
    ManagedSmsRateLimiter limiter = new ManagedSmsRateLimiter(redis, properties());

    assertThat(limiter.tryAcquire(runtime(10L, "client-a"), 60L)).isTrue();

    ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
    verify(redis).execute(any(RedisScript.class), keys.capture(), any(Object[].class));
    assertThat(keys.getValue())
        .contains(
            "unifydesk:managed-sms:rate:global",
            "unifydesk:managed-sms:rate:account:client-a",
            "unifydesk:managed-sms:rate:company:10",
            "unifydesk:managed-sms:rate:run:60");
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void redisFailureFailsClosed() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenThrow(new IllegalStateException("redis unavailable"));
    ManagedSmsRateLimiter limiter = new ManagedSmsRateLimiter(redis, properties());

    assertThat(limiter.tryAcquire(runtime(10L, "client-a"), 60L)).isFalse();
  }

  private ManagedSmsRuntimeProperties properties() {
    ManagedSmsRuntimeProperties properties = new ManagedSmsRuntimeProperties();
    properties.setEnabled(true);
    properties.setPlatformSendingEnabled(true);
    properties.setProvider("MSG91_SMS");
    properties.setMasterAuthKey("secret");
    properties.setGlobalMaxPerSecond(20);
    properties.setNewTenantHourlyCap(100);
    properties.setNewTenantDailyCap(1000);
    return properties;
  }

  private ManagedSmsRuntimeRepository.DeliveryRuntime runtime(Long companyId, String account) {
    return new ManagedSmsRuntimeRepository.DeliveryRuntime(
        companyId,
        60L,
        20L,
        30L,
        40L,
        Instant.now().minusSeconds(864000),
        "READY",
        true,
        41L,
        "pe",
        "ACTIVE",
        "ACTIVE",
        42L,
        "ACMEIN",
        "SERVICE",
        43L,
        44L,
        "dlt",
        "Body",
        "fingerprint",
        "[]",
        "{}",
        45L,
        account,
        "template-ref",
        "flow-ref",
        "rules",
        "ACTIVE",
        "ACTIVE",
        "ACTIVE",
        "ACTIVE",
        true,
        "MESSAGING_MANAGED",
        "ACTIVE");
  }
}

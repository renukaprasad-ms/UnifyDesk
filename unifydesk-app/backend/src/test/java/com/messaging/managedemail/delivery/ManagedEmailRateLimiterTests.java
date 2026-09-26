package com.messaging.managedemail.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.service.EntitlementService;
import com.messaging.managedemail.reputation.ManagedEmailReputationService;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class ManagedEmailRateLimiterTests {

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void accountBucketIsSharedWhileCompanyBucketIsolated() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    EntitlementService entitlement = mock(EntitlementService.class);
    ManagedEmailReputationService reputation = mock(ManagedEmailReputationService.class);
    ManagedEmailRuntimeProperties properties = properties();
    when(entitlement.effectiveSubscription(anyLong())).thenReturn(Optional.empty());
    when(reputation.rateMultiplier(anyLong())).thenReturn(1.0);
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
    ManagedEmailRateLimiter limiter =
        new ManagedEmailRateLimiter(redis, properties, entitlement, reputation);

    assertThat(limiter.tryAcquire(runtime(10L), 100L)).isTrue();
    assertThat(limiter.tryAcquire(runtime(11L), 101L)).isTrue();

    ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
    verify(redis, times(2)).execute(any(RedisScript.class), keys.capture(), any(Object[].class));
    List<List<String>> calls = keys.getAllValues();
    assertThat(calls.get(0).get(0)).isEqualTo(calls.get(1).get(0));
    assertThat(calls.get(0).get(1)).isNotEqualTo(calls.get(1).get(1));
    assertThat(calls.get(0).get(2)).isNotEqualTo(calls.get(1).get(2));
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void rejectedHierarchyUsesSingleAtomicRedisEvaluation() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    EntitlementService entitlement = mock(EntitlementService.class);
    ManagedEmailReputationService reputation = mock(ManagedEmailReputationService.class);
    when(entitlement.effectiveSubscription(anyLong())).thenReturn(Optional.empty());
    when(reputation.rateMultiplier(anyLong())).thenReturn(1.0);
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
    ManagedEmailRateLimiter limiter =
        new ManagedEmailRateLimiter(redis, properties(), entitlement, reputation);

    assertThat(limiter.tryAcquire(runtime(10L), 100L)).isFalse();

    verify(redis, times(1)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    verifyNoMoreInteractions(redis);
  }

  private ManagedEmailRuntimeProperties properties() {
    ManagedEmailRuntimeProperties properties = new ManagedEmailRuntimeProperties();
    properties.setAccountSafetyMaxPerSecond(50);
    properties.setCompanyMaxPerSecond(10);
    properties.setPlanMaxPerSecond(8);
    properties.setRunMaxPerSecond(5);
    properties.setNewTenantMaxPerSecond(2);
    properties.setNewTenantHourlyCap(100);
    properties.setNewTenantDailyCap(1000);
    properties.setEstablishedHourlyCap(1000);
    properties.setEstablishedDailyCap(10000);
    return properties;
  }

  private ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime(Long companyId) {
    return new ManagedEmailRuntimeRepository.ManagedSenderRuntime(
        companyId, 20L, 30L, 50L, 40L, "ACTIVE", "ACTIVE", "MESSAGING_MANAGED", true,
        "ACTIVE", "ENABLED", "HEALTHY", "ESTABLISHED", "ACTIVE", "example.test",
        "us-east-1", "tenant", "cfg", 25.0, 10000.0);
  }
}

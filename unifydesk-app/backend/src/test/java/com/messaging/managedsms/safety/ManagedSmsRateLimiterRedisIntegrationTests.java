package com.messaging.managedsms.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class ManagedSmsRateLimiterRedisIntegrationTests {

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redis;

  @BeforeAll
  static void connect() {
    connectionFactory = new LettuceConnectionFactory("127.0.0.1", 56379);
    connectionFactory.afterPropertiesSet();
    redis = new StringRedisTemplate(connectionFactory);
    redis.afterPropertiesSet();
  }

  @AfterAll
  static void close() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @Test
  void redisAtomicallyEnforcesGlobalAccountCompanyAndRunScopes() {
    assertScopeBlocksSecond(
        properties(0.01, 1000, 1000, 1000),
        runtime(10L, "account-a"),
        60L,
        runtime(11L, "account-b"),
        61L);

    assertScopeBlocksSecond(
        properties(1000, 0.01, 1000, 1000),
        runtime(10L, "account-a"),
        60L,
        runtime(11L, "account-a"),
        61L);

    assertScopeBlocksSecond(
        properties(1000, 1000, 0.01, 1000),
        runtime(10L, "account-a"),
        60L,
        runtime(10L, "account-b"),
        61L);

    assertScopeBlocksSecond(
        properties(1000, 1000, 1000, 0.01),
        runtime(10L, "account-a"),
        60L,
        runtime(10L, "account-a"),
        60L);
  }

  private void assertScopeBlocksSecond(
      ManagedSmsRuntimeProperties properties,
      ManagedSmsRuntimeRepository.DeliveryRuntime firstRuntime,
      Long firstRun,
      ManagedSmsRuntimeRepository.DeliveryRuntime secondRuntime,
      Long secondRun) {
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    ManagedSmsRateLimiter limiter = new ManagedSmsRateLimiter(redis, properties);

    assertThat(limiter.tryAcquire(firstRuntime, firstRun)).isTrue();
    assertThat(limiter.tryAcquire(secondRuntime, secondRun)).isFalse();
  }

  private ManagedSmsRuntimeProperties properties(
      double global, double account, double company, double run) {
    ManagedSmsRuntimeProperties properties = new ManagedSmsRuntimeProperties();
    properties.setEnabled(true);
    properties.setPlatformSendingEnabled(true);
    properties.setProvider("MSG91_SMS");
    properties.setGlobalMaxPerSecond(global);
    properties.setProviderAccountMaxPerSecond(account);
    properties.setCompanyMaxPerSecond(company);
    properties.setRunMaxPerSecond(run);
    properties.setNewTenantHourlyCap(100);
    properties.setNewTenantDailyCap(1000);
    return properties;
  }

  private ManagedSmsRuntimeRepository.DeliveryRuntime runtime(Long companyId, String account) {
    Instant now = Instant.now();
    return new ManagedSmsRuntimeRepository.DeliveryRuntime(
        companyId,
        60L,
        20L,
        30L,
        40L,
        now.minusSeconds(864000),
        "READY",
        true,
        now,
        41L,
        "pe",
        "ACTIVE",
        "ACTIVE",
        now,
        now.plusSeconds(3600),
        42L,
        "ACMEIN",
        "SERVICE",
        "ACTIVE",
        now,
        now.plusSeconds(3600),
        43L,
        44L,
        "dlt",
        "Body",
        "fingerprint",
        "[]",
        "{}",
        "ACTIVE",
        now,
        now.plusSeconds(3600),
        45L,
        "CHILD_PER_COMPANY",
        account,
        "template-ref",
        "flow-ref",
        "rules",
        "ACTIVE",
        now,
        "ACTIVE",
        true,
        "MESSAGING_MANAGED",
        "ACTIVE");
  }
}

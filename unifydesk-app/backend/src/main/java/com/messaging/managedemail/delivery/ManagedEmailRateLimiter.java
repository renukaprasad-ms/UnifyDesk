package com.messaging.managedemail.delivery;

import com.messaging.billing.service.EntitlementService;
import com.messaging.managedemail.reputation.ManagedEmailReputationService;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedEmailRateLimiter {

  private static final DefaultRedisScript<Long> SCRIPT =
      new DefaultRedisScript<>(
          """
          local now = tonumber(ARGV[1])
          local rates = {
            tonumber(ARGV[2]), tonumber(ARGV[3]),
            tonumber(ARGV[4]), tonumber(ARGV[5])
          }
          local capacities = {}
          local nextTokens = {}
          for i = 1, 4 do
            if rates[i] <= 0 then return 0 end
            capacities[i] = math.max(1, rates[i])
            local data = redis.call('HMGET', KEYS[i], 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then tokens = capacities[i] end
            if ts == nil then ts = now end
            local elapsed = math.max(0, now - ts)
            tokens = math.min(capacities[i], tokens + (elapsed * rates[i] / 1000.0))
            if tokens < 1 then return 0 end
            nextTokens[i] = tokens - 1
          end

          local policyAccountDayCap = tonumber(ARGV[6])
          local companyHourCap = tonumber(ARGV[7])
          local companyDayCap = tonumber(ARGV[8])
          if policyAccountDayCap <= 0 or companyHourCap <= 0 or companyDayCap <= 0 then return 0 end
          local accountPolicyCount = tonumber(redis.call('GET', KEYS[5]) or '0')
          local companyHourCount = tonumber(redis.call('GET', KEYS[6]) or '0')
          local companyDayCount = tonumber(redis.call('GET', KEYS[7]) or '0')
          if accountPolicyCount + 1 > policyAccountDayCap
              or companyHourCount + 1 > companyHourCap
              or companyDayCount + 1 > companyDayCap then
            return 0
          end

          for i = 1, 4 do
            redis.call('HMSET', KEYS[i], 'tokens', nextTokens[i], 'ts', now)
            redis.call('PEXPIRE', KEYS[i], 60000)
          end
          redis.call('INCR', KEYS[5])
          redis.call('EXPIRE', KEYS[5], 172800)
          redis.call('INCR', KEYS[6])
          redis.call('EXPIRE', KEYS[6], 7200)
          redis.call('INCR', KEYS[7])
          redis.call('EXPIRE', KEYS[7], 172800)
          return 1
          """,
          Long.class);

  private static final DateTimeFormatter HOUR =
      DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

  private final StringRedisTemplate redisTemplate;
  private final ManagedEmailRuntimeProperties properties;
  private final EntitlementService entitlementService;
  private final ManagedEmailReputationService reputationService;

  public boolean tryAcquire(
      ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime, Long runId) {
    double providerRate =
        runtime.providerMaxSendRate() == null ? 0.0 : runtime.providerMaxSendRate();
    double safetyRate = properties.getAccountSafetyMaxPerSecond();
    long policyDaily = properties.getAccountDailySafetyCap();
    if (providerRate <= 0 || safetyRate <= 0 || policyDaily <= 0) {
      return false;
    }

    double reputationMultiplier = reputationService.rateMultiplier(runtime.companyId());
    if (reputationMultiplier <= 0) return false;

    double accountRate = Math.min(providerRate, safetyRate);
    double companyRate =
        Math.min(properties.getCompanyMaxPerSecond(), accountRate) * reputationMultiplier;
    if (isNewTenant(runtime.rampStage())) {
      companyRate = Math.min(companyRate, properties.getNewTenantMaxPerSecond());
    }

    long planId =
        entitlementService
            .effectiveSubscription(runtime.companyId())
            .map(subscription -> subscription.getPlan().getId())
            .orElse(0L);
    double configuredPlanRate = properties.getPlanMaxPerSecond();
    double planRate =
        configuredPlanRate > 0 ? Math.min(configuredPlanRate, companyRate) : companyRate;
    double configuredRunRate = properties.getRunMaxPerSecond();
    double runRate =
        configuredRunRate > 0 ? Math.min(configuredRunRate, planRate) : planRate;

    long companyHourlyCap =
        isNewTenant(runtime.rampStage())
            ? properties.getNewTenantHourlyCap()
            : properties.getEstablishedHourlyCap();
    long companyDailyCap =
        isNewTenant(runtime.rampStage())
            ? properties.getNewTenantDailyCap()
            : properties.getEstablishedDailyCap();

    Instant now = Instant.now();
    String day = DAY.format(now);
    List<String> keys =
        List.of(
            "unifydesk:managed-email:rate:account:" + runtime.region(),
            "unifydesk:managed-email:rate:company:" + runtime.companyId(),
            "unifydesk:managed-email:rate:plan:" + planId + ":company:" + runtime.companyId(),
            "unifydesk:managed-email:rate:run:" + runId,
            "unifydesk:managed-email:policy:account-day:" + runtime.region() + ":" + day,
            "unifydesk:managed-email:policy:company-hour:"
                + runtime.companyId()
                + ":"
                + HOUR.format(now),
            "unifydesk:managed-email:policy:company-day:" + runtime.companyId() + ":" + day);

    Long allowed =
        redisTemplate.execute(
            SCRIPT,
            keys,
            Long.toString(System.currentTimeMillis()),
            Double.toString(accountRate),
            Double.toString(companyRate),
            Double.toString(planRate),
            Double.toString(runRate),
            Long.toString(policyDaily),
            Long.toString(companyHourlyCap),
            Long.toString(companyDailyCap));
    return Long.valueOf(1L).equals(allowed);
  }

  private boolean isNewTenant(String rampStage) {
    return "NEW".equals(rampStage) || "RAMPING".equals(rampStage);
  }
}

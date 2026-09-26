package com.messaging.managedemail.control.security;

import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.exception.TooManyRequestsException;
import com.messaging.managedemail.control.config.ManagedEmailProperties;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedEmailControlPlaneRateLimiter {

  private static final DefaultRedisScript<Long> SCRIPT =
      new DefaultRedisScript<>(
          """
          local companyCount = tonumber(redis.call('GET', KEYS[1]) or '0')
          local userCount = tonumber(redis.call('GET', KEYS[2]) or '0')
          local companyLimit = tonumber(ARGV[1])
          local userLimit = tonumber(ARGV[2])
          local ttlMillis = tonumber(ARGV[3])
          if companyCount + 1 > companyLimit or userCount + 1 > userLimit then
            return 0
          end
          local nextCompany = redis.call('INCR', KEYS[1])
          if nextCompany == 1 then redis.call('PEXPIRE', KEYS[1], ttlMillis) end
          local nextUser = redis.call('INCR', KEYS[2])
          if nextUser == 1 then redis.call('PEXPIRE', KEYS[2], ttlMillis) end
          return 1
          """,
          Long.class);

  private final StringRedisTemplate redisTemplate;
  private final ManagedEmailProperties properties;

  public void requireAllowed(Operation operation, Long companyId, Long userId) {
    Limit limit = limit(operation);
    Duration window = properties.getControlRateLimitWindow();
    if (window == null || window.isZero() || window.isNegative()) {
      throw new ServiceUnavailableException("Managed Email request protection is unavailable");
    }
    List<String> keys =
        List.of(
            key(operation, "company", companyId),
            key(operation, "user", userId));
    try {
      Long allowed =
          redisTemplate.execute(
              SCRIPT,
              keys,
              Integer.toString(limit.companyLimit()),
              Integer.toString(limit.userLimit()),
              Long.toString(window.toMillis()));
      if (!Long.valueOf(1L).equals(allowed)) {
        throw new TooManyRequestsException("Too many Managed Email requests. Try again later.");
      }
    } catch (TooManyRequestsException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new ServiceUnavailableException(
          "Managed Email request protection is unavailable", exception);
    }
  }

  private Limit limit(Operation operation) {
    return switch (operation) {
      case CUSTOMER_DOMAIN_CREATE ->
          new Limit(
              properties.getCustomerDomainCreateCompanyLimit(),
              properties.getCustomerDomainCreateUserLimit());
      case HOSTED_DOMAIN_CREATE ->
          new Limit(
              properties.getHostedDomainCreateCompanyLimit(),
              properties.getHostedDomainCreateUserLimit());
      case DOMAIN_REFRESH ->
          new Limit(
              properties.getDomainRefreshCompanyLimit(),
              properties.getDomainRefreshUserLimit());
      case DOMAIN_DELETE ->
          new Limit(
              properties.getDomainDeleteCompanyLimit(),
              properties.getDomainDeleteUserLimit());
      case SENDER_CREATE ->
          new Limit(
              properties.getSenderCreateCompanyLimit(),
              properties.getSenderCreateUserLimit());
      case SENDER_DISABLE ->
          new Limit(
              properties.getSenderDisableCompanyLimit(),
              properties.getSenderDisableUserLimit());
    };
  }

  private String key(Operation operation, String scope, Long id) {
    return "unifydesk:managed-email:control:"
        + operation.name().toLowerCase(java.util.Locale.ROOT)
        + ":"
        + scope
        + ":"
        + id;
  }

  public enum Operation {
    CUSTOMER_DOMAIN_CREATE,
    HOSTED_DOMAIN_CREATE,
    DOMAIN_REFRESH,
    DOMAIN_DELETE,
    SENDER_CREATE,
    SENDER_DISABLE
  }

  private record Limit(int companyLimit, int userLimit) {
    private Limit {
      if (companyLimit <= 0 || userLimit <= 0) {
        throw new ServiceUnavailableException("Managed Email request protection is unavailable");
      }
    }
  }
}

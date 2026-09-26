package com.messaging.managedemail.control.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.exception.TooManyRequestsException;
import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.security.ManagedEmailControlPlaneRateLimiter.Operation;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class ManagedEmailControlPlaneRateLimiterTests {

  @Mock private StringRedisTemplate redisTemplate;

  private ManagedEmailControlPlaneRateLimiter limiter;

  @BeforeEach
  void setUp() {
    ManagedEmailProperties properties = new ManagedEmailProperties();
    properties.setControlRateLimitWindow(Duration.ofMinutes(1));
    properties.setCustomerDomainCreateCompanyLimit(2);
    properties.setCustomerDomainCreateUserLimit(2);
    limiter = new ManagedEmailControlPlaneRateLimiter(redisTemplate, properties);
  }

  @Test
  void burstIsDeniedAfterAtomicCompanyAndUserBudgetIsExhausted() {
    doReturn(1L, 1L, 0L)
        .when(redisTemplate)
        .execute(any(RedisScript.class), anyList(), any(Object[].class));

    limiter.requireAllowed(Operation.CUSTOMER_DOMAIN_CREATE, 1L, 7L);
    limiter.requireAllowed(Operation.CUSTOMER_DOMAIN_CREATE, 1L, 7L);

    assertThatThrownBy(
            () -> limiter.requireAllowed(Operation.CUSTOMER_DOMAIN_CREATE, 1L, 7L))
        .isInstanceOf(TooManyRequestsException.class);
  }

  @Test
  void redisFailureFailsClosed() {
    doThrow(new IllegalStateException("redis unavailable"))
        .when(redisTemplate)
        .execute(any(RedisScript.class), anyList(), any(Object[].class));

    assertThatThrownBy(
            () -> limiter.requireAllowed(Operation.CUSTOMER_DOMAIN_CREATE, 1L, 7L))
        .isInstanceOf(ServiceUnavailableException.class);
  }
}

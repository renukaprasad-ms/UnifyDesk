package com.messaging.notification.platform.delivery.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.push.WebPushGateway.PushSendResult;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class StandardWebPushGatewayTests {

  private final StandardWebPushGateway gateway =
      new StandardWebPushGateway(new NotificationDeliveryProperties());

  @Test
  void classifiesAcceptedAndExpiredSubscriptions() {
    assertThat(gateway.classifyStatus(201, null).outcome())
        .isEqualTo(PushSendResult.Outcome.ACCEPTED);
    assertThat(gateway.classifyStatus(404, null).outcome())
        .isEqualTo(PushSendResult.Outcome.EXPIRED);
    assertThat(gateway.classifyStatus(410, null).outcome())
        .isEqualTo(PushSendResult.Outcome.EXPIRED);
  }

  @Test
  void classifiesRateLimitAndTransientProviderFailures() {
    Duration retryAfter = Duration.ofSeconds(90);
    var limited = gateway.classifyStatus(429, retryAfter);

    assertThat(limited.outcome()).isEqualTo(PushSendResult.Outcome.RATE_LIMITED);
    assertThat(limited.retryAfter()).isEqualTo(retryAfter);
    assertThat(gateway.classifyStatus(503, null).outcome())
        .isEqualTo(PushSendResult.Outcome.TRANSIENT_FAILURE);
  }

  @Test
  void authFailureIsPermanentAndDoesNotHotLoop() {
    assertThat(gateway.classifyStatus(401, null).outcome())
        .isEqualTo(PushSendResult.Outcome.PERMANENT_FAILURE);
    assertThat(gateway.classifyStatus(403, null).outcome())
        .isEqualTo(PushSendResult.Outcome.PERMANENT_FAILURE);
  }
}

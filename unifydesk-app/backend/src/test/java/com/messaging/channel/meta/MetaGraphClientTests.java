package com.messaging.channel.meta;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.meta.control.MetaGraphException.SafeCategory;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MetaGraphClientTests {

  @Test
  void parsesSafeGraphDiagnosticsWithoutLeakingRawProviderBody() {
    MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
    properties.setAppId("app-id");
    properties.setAppSecret("app-secret");
    properties.setGraphApiVersion("v26.0");
    MetaGraphClient client = new MetaGraphClient(properties, new ObjectMapper());

    byte[] body =
        """
        {"error":{"message":"secret-token-value must never leak","type":"OAuthException","code":190,"error_subcode":463,"fbtrace_id":"trace-1"}}
        """
            .getBytes(StandardCharsets.UTF_8);

    var failure = client.parseGraphFailure("debug_token", 401, body);

    assertThat(failure.httpStatus()).isEqualTo(401);
    assertThat(failure.metaType()).isEqualTo("OAuthException");
    assertThat(failure.metaCode()).isEqualTo(190);
    assertThat(failure.metaSubcode()).isEqualTo(463);
    assertThat(failure.fbtraceId()).isEqualTo("trace-1");
    assertThat(failure.safeCategory()).isEqualTo(SafeCategory.AUTH);
    assertThat(failure.retryable()).isFalse();
    assertThat(failure.getMessage()).doesNotContain("secret-token-value");
  }

  @Test
  void categorizesProviderRateLimitAsRetryable() {
    MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
    MetaGraphClient client = new MetaGraphClient(properties, new ObjectMapper());

    byte[] body =
        """
        {"error":{"type":"OAuthException","code":4,"fbtrace_id":"t"}}
        """
            .getBytes(StandardCharsets.UTF_8);

    var failure = client.parseGraphFailure("subscribe", 429, body);

    assertThat(failure.safeCategory()).isEqualTo(SafeCategory.RATE_LIMIT);
    assertThat(failure.retryable()).isTrue();
  }
}

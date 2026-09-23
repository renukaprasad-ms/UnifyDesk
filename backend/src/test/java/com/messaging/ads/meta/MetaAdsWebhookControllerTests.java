package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MetaAdsWebhookControllerTests {

  private static final String APP_SECRET = "meta-ads-app-secret";
  private MetaAdsWebhookController controller;

  @BeforeEach
  void setUp() {
    MetaAdsProperties properties = new MetaAdsProperties();
    properties.setAppSecret(APP_SECRET);
    properties.setWebhookVerifyToken("verify-meta-ads");
    controller = new MetaAdsWebhookController(properties, new ObjectMapper());
  }

  @Test
  void returnsChallengeWhenVerifyTokenMatches() {
    var response =
        controller.verify("subscribe", "verify-meta-ads", "challenge-value");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("challenge-value");
  }

  @Test
  void rejectsWebhookVerificationWhenTokenDoesNotMatch() {
    var response =
        controller.verify("subscribe", "wrong-token", "challenge-value");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void acceptsPayloadWithValidMetaSignature() throws Exception {
    String body = "{"object":"ad_account","entry":[]}";
    String signature = "sha256=" + signature(body);

    var response = controller.receive(body, signature);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("EVENT_RECEIVED");
  }

  @Test
  void rejectsPayloadWithInvalidMetaSignature() {
    var response =
        controller.receive(
            "{"object":"ad_account","entry":[]}",
            "sha256=0000000000000000000000000000000000000000000000000000000000000000");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private String signature(String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }
}

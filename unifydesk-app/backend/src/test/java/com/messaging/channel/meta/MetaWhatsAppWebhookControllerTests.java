package com.messaging.channel.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.observability.webhook.WebhookCallbackAuditService;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class MetaWhatsAppWebhookControllerTests {

  private static final String APP_SECRET = "meta-app-secret";
  private MetaWhatsAppWebhookController controller;

  @BeforeEach
  void setUp() {
    MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
    properties.setAppSecret(APP_SECRET);
    properties.setWebhookVerifyToken("verify-me");
    controller =
        new MetaWhatsAppWebhookController(
            properties, mock(WebhookCallbackAuditService.class), new ObjectMapper());
  }

  @Test
  void returnsChallengeWhenVerifyTokenMatches() {
    var response =
        controller.verify(
            "subscribe",
            "verify-me",
            "challenge-value",
            new HttpHeaders(),
            request("GET"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("challenge-value");
  }

  @Test
  void rejectsWebhookVerificationWhenTokenDoesNotMatch() {
    var response =
        controller.verify(
            "subscribe",
            "wrong-token",
            "challenge-value",
            new HttpHeaders(),
            request("GET"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void acceptsPayloadWithValidMetaSignature() throws Exception {
    String body = "{\"object\":\"whatsapp_business_account\"}";
    String signature = "sha256=" + signature(body);

    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Hub-Signature-256", signature);
    var response = controller.receive(body, signature, headers, request("POST"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("EVENT_RECEIVED");
  }

  @Test
  void rejectsPayloadWithInvalidMetaSignature() {
    var response =
        controller.receive(
            "{\"object\":\"whatsapp_business_account\"}",
            "sha256=0000000000000000000000000000000000000000000000000000000000000000",
            new HttpHeaders(),
            request("POST"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private MockHttpServletRequest request(String method) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/webhooks/meta/whatsapp");
    request.setRemoteAddr("127.0.0.1");
    return request;
  }

  private String signature(String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }
}

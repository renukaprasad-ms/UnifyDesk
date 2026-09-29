package com.messaging.channel.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.meta.runtime.MetaWhatsAppRuntimeProperties;
import com.messaging.channel.meta.runtime.MetaWhatsAppWebhookEventStore;
import com.messaging.channel.meta.runtime.MetaWhatsAppWebhookNormalizer;
import com.messaging.channel.meta.runtime.MetaWhatsAppWebhookSecrets;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

class MetaWhatsAppWebhookControllerTests {

  private static final String APP_SECRET = "meta-app-secret";
  private MetaWhatsAppWebhookController controller;
  private MetaWhatsAppWebhookEventStore eventStore;
  private MetaWhatsAppRuntimeProperties runtimeProperties;

  @BeforeEach
  void setUp() {
    runtimeProperties = new MetaWhatsAppRuntimeProperties();
    eventStore = mock(MetaWhatsAppWebhookEventStore.class);
    MetaWhatsAppWebhookNormalizer normalizer =
        new MetaWhatsAppWebhookNormalizer(new ObjectMapper(), runtimeProperties);
    controller =
        new MetaWhatsAppWebhookController(
            new MetaWhatsAppWebhookSecrets(APP_SECRET, "verify-me"),
            runtimeProperties,
            normalizer,
            eventStore);
  }

  @Test
  void returnsChallengeWhenVerifyTokenMatches() {
    var response = controller.verify("subscribe", "verify-me", "challenge-value");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("challenge-value");
  }

  @Test
  void rejectsWebhookVerificationWhenTokenDoesNotMatch() {
    var response = controller.verify("subscribe", "wrong-token", "challenge-value");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void acceptsPayloadWithValidMetaSignature() throws Exception {
    byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
    var response = controller.receiveExactBytes(body, "sha256=" + signature(body), (long) body.length);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("EVENT_RECEIVED");
    verify(eventStore).persist(anyList());
  }

  @Test
  void rejectsPayloadWithInvalidMetaSignatureWithoutStoring() {
    byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
    var response =
        controller.receiveExactBytes(
            body,
            "sha256=0000000000000000000000000000000000000000000000000000000000000000",
            (long) body.length);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verify(eventStore, never()).persist(anyList());
  }

  @Test
  void rejectsUnsignedPayloadWithoutStoring() {
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    var response = controller.receiveExactBytes(body, null, (long) body.length);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verify(eventStore, never()).persist(anyList());
  }

  @Test
  void rejectsSignedMalformedPayloadWithoutStoring() throws Exception {
    byte[] body = "{bad-json".getBytes(StandardCharsets.UTF_8);
    var response = controller.receiveExactBytes(body, "sha256=" + signature(body), (long) body.length);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    verify(eventStore, never()).persist(anyList());
  }

  @Test
  void rejectsOversizedSignedPayloadBeforeStorage() throws Exception {
    runtimeProperties.setWebhookMaxBody(DataSize.ofBytes(4));
    byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
    var response = controller.receiveExactBytes(body, "sha256=" + signature(body), (long) body.length);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    verify(eventStore, never()).persist(anyList());
  }

  @Test
  void malformedOrUppercasePrefixSignatureIsRejected() throws Exception {
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    var response = controller.receiveExactBytes(body, "SHA256=" + signature(body), (long) body.length);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verify(eventStore, never()).persist(anyList());
  }

  private String signature(byte[] body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(body));
  }
}

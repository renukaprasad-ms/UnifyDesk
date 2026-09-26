package com.messaging.managedemail.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SnsMessageVerifierTests {

  private static final String TOPIC =
      "arn:aws:sns:us-east-1:123456789012:managed-email-events";
  private static final String CERT =
      "https://sns.us-east-1.amazonaws.com/SimpleNotificationService-test.pem";

  @Test
  void validSignedNotificationIsAccepted() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    ManagedEmailRuntimeProperties properties = properties();
    SnsCertificateLoader loader = mock(SnsCertificateLoader.class);
    KeyPair keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    X509Certificate certificate = mock(X509Certificate.class);
    when(certificate.getPublicKey()).thenReturn(keys.getPublic());
    when(loader.load(any(), anyInt())).thenReturn(certificate);
    ObjectNode body = notification(mapper, TOPIC, Instant.now(), CERT);
    body.put("Signature", sign(keys, canonical(body)));
    SnsMessageVerifier verifier = new SnsMessageVerifier(mapper, loader, properties);

    SnsEnvelope envelope = verifier.verify(mapper.writeValueAsBytes(body));

    assertThat(envelope.messageId()).isEqualTo("sns-1");
    assertThat(envelope.topicArn()).isEqualTo(TOPIC);
  }

  @Test
  void forgedSignatureIsRejected() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    SnsCertificateLoader loader = mock(SnsCertificateLoader.class);
    KeyPair trusted = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    KeyPair attacker = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    X509Certificate certificate = mock(X509Certificate.class);
    when(certificate.getPublicKey()).thenReturn(trusted.getPublic());
    when(loader.load(any(), anyInt())).thenReturn(certificate);
    ObjectNode body = notification(mapper, TOPIC, Instant.now(), CERT);
    body.put("Signature", sign(attacker, canonical(body)));

    assertThatThrownBy(
            () ->
                new SnsMessageVerifier(mapper, loader, properties())
                    .verify(mapper.writeValueAsBytes(body)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid SNS signature");
  }

  @Test
  void wrongTopicAndInvalidCertificateHostAreRejectedBeforeFetch() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    SnsCertificateLoader loader = mock(SnsCertificateLoader.class);
    ObjectNode wrongTopic =
        notification(
            mapper,
            "arn:aws:sns:us-east-1:123456789012:other",
            Instant.now(),
            CERT);
    assertThatThrownBy(
            () ->
                new SnsMessageVerifier(mapper, loader, properties())
                    .verify(mapper.writeValueAsBytes(wrongTopic)))
        .isInstanceOf(IllegalArgumentException.class);

    ObjectNode badCert =
        notification(mapper, TOPIC, Instant.now(), "https://example.test/cert.pem");
    assertThatThrownBy(
            () ->
                new SnsMessageVerifier(mapper, loader, properties())
                    .verify(mapper.writeValueAsBytes(badCert)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(loader);
  }

  @Test
  void staleNotificationIsRejectedAsReplay() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    SnsCertificateLoader loader = mock(SnsCertificateLoader.class);
    ObjectNode body = notification(mapper, TOPIC, Instant.now().minusSeconds(3600), CERT);

    assertThatThrownBy(
            () ->
                new SnsMessageVerifier(mapper, loader, properties())
                    .verify(mapper.writeValueAsBytes(body)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SNS message is outside replay window");
    verifyNoInteractions(loader);
  }

  private ManagedEmailRuntimeProperties properties() {
    ManagedEmailRuntimeProperties properties = new ManagedEmailRuntimeProperties();
    properties.setRegion("us-east-1");
    properties.setSnsTopicArn(TOPIC);
    properties.setSnsReplayWindowSeconds(300);
    return properties;
  }

  private ObjectNode notification(
      ObjectMapper mapper, String topic, Instant timestamp, String certificateUrl) {
    ObjectNode node = mapper.createObjectNode();
    node.put("Type", "Notification");
    node.put("MessageId", "sns-1");
    node.put("TopicArn", topic);
    node.put("Timestamp", timestamp.toString());
    node.put("SignatureVersion", "2");
    node.put("SigningCertURL", certificateUrl);
    node.put("Signature", Base64.getEncoder().encodeToString(new byte[] {1}));
    node.put("Message", "{\"eventType\":\"Delivery\"}");
    return node;
  }

  private String canonical(ObjectNode node) {
    return "Message\n"
        + node.get("Message").asText()
        + "\nMessageId\n"
        + node.get("MessageId").asText()
        + "\nTimestamp\n"
        + node.get("Timestamp").asText()
        + "\nTopicArn\n"
        + node.get("TopicArn").asText()
        + "\nType\n"
        + node.get("Type").asText()
        + "\n";
  }

  private String sign(KeyPair pair, String canonical) throws Exception {
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(pair.getPrivate());
    signature.update(canonical.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(signature.sign());
  }
}

package com.messaging.managedemail.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SnsMessageVerifier {

  private final ObjectMapper objectMapper;
  private final SnsCertificateLoader certificateLoader;
  private final ManagedEmailRuntimeProperties properties;

  public SnsEnvelope verify(byte[] body) {
    if (body == null
        || body.length == 0
        || body.length > Math.max(1, properties.getWebhookMaxBytes())) {
      throw new IllegalArgumentException("Invalid SNS request size");
    }
    try {
      JsonNode root = objectMapper.readTree(body);
      validateDepth(root, 1);
      String type = required(root, "Type");
      String messageId = required(root, "MessageId");
      String topicArn = required(root, "TopicArn");
      String timestampText = required(root, "Timestamp");
      String signatureVersion = required(root, "SignatureVersion");
      String signatureText = required(root, "Signature");
      String signingCertUrl = required(root, "SigningCertURL");
      String message = required(root, "Message");

      validateTopic(topicArn);
      Instant timestamp = Instant.parse(timestampText);
      long replayWindow = Math.max(1, properties.getSnsReplayWindowSeconds());
      if (Duration.between(timestamp, Instant.now()).abs().compareTo(Duration.ofSeconds(replayWindow))
          > 0) {
        throw new IllegalArgumentException("SNS message is outside replay window");
      }

      URI certificateUri = validateAwsHttpsUri(signingCertUrl, topicArn, true);
      X509Certificate certificate =
          certificateLoader.load(certificateUri, properties.getSigningCertificateMaxBytes());
      certificate.checkValidity();

      String algorithm =
          switch (signatureVersion) {
            case "1" -> "SHA1withRSA";
            case "2" -> "SHA256withRSA";
            default -> throw new IllegalArgumentException("Unsupported SNS signature version");
          };
      Signature verifier = Signature.getInstance(algorithm);
      verifier.initVerify(certificate.getPublicKey());
      verifier.update(canonical(root, type).getBytes(StandardCharsets.UTF_8));
      if (!verifier.verify(Base64.getDecoder().decode(signatureText))) {
        throw new IllegalArgumentException("Invalid SNS signature");
      }

      String subscribeUrl = root.path("SubscribeURL").asText(null);
      if (subscribeUrl != null && !subscribeUrl.isBlank()) {
        validateAwsHttpsUri(subscribeUrl, topicArn, false);
      }
      return new SnsEnvelope(type, messageId, topicArn, timestamp, message, subscribeUrl);
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid SNS message", exception);
    }
  }

  public URI validateAwsHttpsUri(String value, String topicArn, boolean certificate) {
    URI uri = URI.create(value);
    String[] arn = topicArn.split(":", 6);
    if (arn.length != 6) throw new IllegalArgumentException("Invalid SNS TopicArn");
    String partition = arn[1];
    String region = arn[3];
    String suffix =
        switch (partition) {
          case "aws-cn" -> "amazonaws.com.cn";
          case "aws", "aws-us-gov" -> "amazonaws.com";
          default -> throw new IllegalArgumentException("Unsupported SNS partition");
        };
    String expectedHost = "sns." + region + "." + suffix;
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getUserInfo() != null
        || uri.getPort() != -1
        || uri.getHost() == null
        || !expectedHost.equalsIgnoreCase(uri.getHost())
        || uri.getFragment() != null) {
      throw new IllegalArgumentException("Invalid SNS AWS URL");
    }
    if (certificate) {
      String path = uri.getPath();
      if (path == null
          || !path.startsWith("/SimpleNotificationService-")
          || !path.endsWith(".pem")) {
        throw new IllegalArgumentException("Invalid SNS signing certificate URL");
      }
    }
    return uri;
  }

  private void validateTopic(String topicArn) {
    String configured = properties.getSnsTopicArn();
    if (configured == null || configured.isBlank() || !configured.equals(topicArn)) {
      throw new IllegalArgumentException("SNS TopicArn is not allowed");
    }
    String[] arn = topicArn.split(":", 6);
    if (arn.length != 6
        || !"sns".equals(arn[2])
        || properties.getRegion() == null
        || properties.getRegion().isBlank()
        || !properties.getRegion().equals(arn[3])) {
      throw new IllegalArgumentException("SNS TopicArn region is invalid");
    }
  }

  private String canonical(JsonNode root, String type) {
    StringBuilder value = new StringBuilder();
    if ("Notification".equals(type)) {
      append(value, "Message", required(root, "Message"));
      append(value, "MessageId", required(root, "MessageId"));
      if (root.hasNonNull("Subject")) append(value, "Subject", root.get("Subject").asText());
      append(value, "Timestamp", required(root, "Timestamp"));
      append(value, "TopicArn", required(root, "TopicArn"));
      append(value, "Type", type);
      return value.toString();
    }
    if ("SubscriptionConfirmation".equals(type)
        || "UnsubscribeConfirmation".equals(type)) {
      append(value, "Message", required(root, "Message"));
      append(value, "MessageId", required(root, "MessageId"));
      append(value, "SubscribeURL", required(root, "SubscribeURL"));
      append(value, "Timestamp", required(root, "Timestamp"));
      append(value, "Token", required(root, "Token"));
      append(value, "TopicArn", required(root, "TopicArn"));
      append(value, "Type", type);
      return value.toString();
    }
    throw new IllegalArgumentException("Unsupported SNS message type");
  }

  private void append(StringBuilder builder, String name, String value) {
    builder.append(name).append('\n').append(value).append('\n');
  }

  private String required(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || value.asText().isBlank()) {
      throw new IllegalArgumentException("Missing SNS field " + field);
    }
    return value.asText();
  }

  private void validateDepth(JsonNode node, int depth) {
    if (depth > Math.max(1, properties.getWebhookMaxJsonDepth())) {
      throw new IllegalArgumentException("SNS JSON exceeds depth limit");
    }
    if (node.isContainerNode()) {
      node.elements().forEachRemaining(child -> validateDepth(child, depth + 1));
    }
  }
}

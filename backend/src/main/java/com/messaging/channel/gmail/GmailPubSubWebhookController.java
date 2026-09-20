package com.messaging.channel.gmail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/google/gmail")
@RequiredArgsConstructor
public class GmailPubSubWebhookController {

  private final GmailOAuthProperties properties;
  private final GmailRealtimeStateService stateService;
  private final ChannelConnectionRepository connectionRepository;
  private final ObjectMapper objectMapper;

  @PostMapping
  public ResponseEntity<Void> receive(
      @RequestParam(required = false) String token,
      @RequestBody(required = false) PubSubEnvelope envelope) {
    if (!properties.realtimeConfigured()
        || !secureEquals(token, properties.getPubsubPushVerificationToken())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    if (envelope == null
        || envelope.message() == null
        || envelope.message().data() == null
        || envelope.message().data().isBlank()) {
      return ResponseEntity.noContent().build();
    }

    GmailPushData pushData;
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(envelope.message().data());
      pushData = objectMapper.readValue(decoded, GmailPushData.class);
    } catch (Exception exception) {
      log.warn(
          "gmail_pubsub_invalid_payload messageId={} reason={}",
          envelope.message().messageId(),
          exception.getClass().getSimpleName());
      // Ack malformed provider payloads so Pub/Sub does not retry them forever.
      return ResponseEntity.noContent().build();
    }

    if (pushData.emailAddress() == null
        || pushData.emailAddress().isBlank()
        || pushData.historyId() == null
        || pushData.historyId().isBlank()) {
      return ResponseEntity.noContent().build();
    }

    List<ChannelConnection> connections =
        connectionRepository.findByProviderTypeAndExternalAccountIdIgnoreCaseAndStatus(
            ProviderType.GMAIL,
            pushData.emailAddress().trim(),
            ChannelConnectionStatus.ACTIVE);

    int enqueued = 0;
    for (ChannelConnection connection : connections) {
      stateService.ensureState(connection);
      if (stateService.enqueueNotification(connection.getId(), pushData.historyId())) {
        enqueued++;
      }
    }

    log.debug(
        "gmail_pubsub_received account={} historyId={} matchedConnections={} enqueued={}",
        pushData.emailAddress(),
        pushData.historyId(),
        connections.size(),
        enqueued);

    return ResponseEntity.noContent().build();
  }

  private boolean secureEquals(String provided, String expected) {
    if (provided == null || expected == null) return false;
    return MessageDigest.isEqual(
        provided.getBytes(StandardCharsets.UTF_8),
        expected.getBytes(StandardCharsets.UTF_8));
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PubSubEnvelope(PubSubMessage message, String subscription) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PubSubMessage(
      String data,
      @JsonProperty("messageId") String messageId,
      @JsonProperty("publishTime") String publishTime) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GmailPushData(String emailAddress, String historyId) {}
}

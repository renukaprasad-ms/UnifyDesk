package com.messaging.broadcast.msg91;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.transport.BroadcastSmsCommand;
import com.messaging.broadcast.transport.BroadcastSmsResult;
import com.messaging.broadcast.transport.BroadcastTransportException;
import com.messaging.channel.credential.CredentialCipher;
import com.messaging.common.exception.BadRequestException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Msg91Gateway {

  private static final URI SEND_URI = URI.create("https://control.msg91.com/api/v5/flow");
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  private final CredentialCipher credentialCipher;
  private final ObjectMapper objectMapper;
  private final BroadcastProperties properties;

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public Msg91AccountSnapshot inspect(
      BroadcastSenderConnection connection, String senderId) {
    requireProvider(connection);
    String authKey = authKey(connection);
    Msg91ProviderConfig config = config(connection);
    if (authKey.isBlank() || config.flowId().isBlank() || senderId == null || senderId.isBlank()) {
      throw new BadRequestException("MSG91 auth key, Flow ID and Sender ID are required");
    }
    return new Msg91AccountSnapshot(
        true, Math.max(0.0, properties.getMsg91MaxPerSecondCap()), senderId.trim());
  }

  public BroadcastSmsResult send(
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastSmsCommand command) {
    requireProvider(connection);
    String authKey = authKey(connection);
    Msg91ProviderConfig config = config(connection);

    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("flow_id", config.flowId());
    payload.put("sender", sender.getFromAddress());
    if (config.route() != null && !config.route().isBlank()) {
      payload.put("route", config.route());
    }

    ObjectNode recipient = payload.putArray("recipients").addObject();
    recipient.put("mobiles", normalizeMobile(command.recipient()));
    recipient.put(config.messageVariable(), command.bodyText());

    HttpRequest request =
        HttpRequest.newBuilder(SEND_URI)
            .timeout(REQUEST_TIMEOUT)
            .header("accept", "application/json")
            .header("authkey", authKey)
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      JsonNode body = parse(response.body());
      String type = body.path("type").asText("");
      String message = body.path("message").asText("");

      if (status >= 200 && status < 300 && "success".equalsIgnoreCase(type)) {
        return new BroadcastSmsResult(
            message.isBlank() ? command.correlationId() : message,
            Instant.now());
      }

      String reason = message.isBlank() ? "MSG91 rejected the SMS" : message;
      if (status == 429 || status >= 500) {
        throw BroadcastTransportException.retryable(
            "MSG91_HTTP_" + status, reason, null);
      }
      throw BroadcastTransportException.permanent(
          "MSG91_HTTP_" + status, reason, null);
    } catch (BroadcastTransportException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw BroadcastTransportException.unknown(
          "MSG91_INTERRUPTED", "MSG91 send outcome could not be confirmed", exception);
    } catch (IOException exception) {
      throw BroadcastTransportException.unknown(
          "MSG91_SEND_UNKNOWN", "MSG91 send outcome could not be confirmed", exception);
    }
  }

  private JsonNode parse(String body) {
    if (body == null || body.isBlank()) return objectMapper.createObjectNode();
    try {
      return objectMapper.readTree(body);
    } catch (Exception exception) {
      return objectMapper.createObjectNode().put("message", body);
    }
  }

  private Msg91ProviderConfig config(BroadcastSenderConnection connection) {
    if (connection.getProviderConfigJson() == null
        || connection.getProviderConfigJson().isBlank()) {
      throw new BadRequestException("MSG91 provider configuration is missing");
    }
    try {
      Msg91ProviderConfig config =
          objectMapper.readValue(connection.getProviderConfigJson(), Msg91ProviderConfig.class);
      if (config.flowId() == null || config.flowId().isBlank()) {
        throw new BadRequestException("MSG91 Flow ID is missing");
      }
      String variable =
          config.messageVariable() == null || config.messageVariable().isBlank()
              ? "VAR1"
              : config.messageVariable().trim();
      return new Msg91ProviderConfig(
          config.flowId().trim(), variable, config.route() == null ? null : config.route().trim());
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Unable to read MSG91 provider configuration");
    }
  }

  private String authKey(BroadcastSenderConnection connection) {
    if (connection.getEncryptedCredentials() == null
        || connection.getEncryptedCredentials().isBlank()) {
      throw new BadRequestException("MSG91 auth key is missing");
    }
    try {
      String json = credentialCipher.decrypt(connection.getEncryptedCredentials());
      Msg91StoredCredentials credentials =
          objectMapper.readValue(json, Msg91StoredCredentials.class);
      if (credentials.authKey() == null || credentials.authKey().isBlank()) {
        throw new BadRequestException("MSG91 auth key is missing");
      }
      return credentials.authKey().trim();
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Unable to read MSG91 credentials");
    }
  }

  private String normalizeMobile(String value) {
    if (value == null) throw new BadRequestException("SMS recipient is required");
    String digits = value.replaceAll("[^0-9]", "");
    if (digits.length() < 8 || digits.length() > 15) {
      throw new BadRequestException(
          "SMS recipient must be in international format with country code");
    }
    return digits;
  }

  private void requireProvider(BroadcastSenderConnection connection) {
    if (connection.getProvider() != BroadcastSenderProvider.MSG91) {
      throw new IllegalArgumentException(
          "MSG91 gateway cannot handle provider " + connection.getProvider());
    }
  }
}

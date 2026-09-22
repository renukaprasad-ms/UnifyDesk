package com.messaging.broadcast.msg91;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.transport.BroadcastEmailCommand;
import com.messaging.broadcast.transport.BroadcastEmailResult;
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
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Msg91EmailGateway {

  private static final URI SEND_URI =
      URI.create("https://control.msg91.com/api/v5/email/send");
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  private final CredentialCipher credentialCipher;
  private final ObjectMapper objectMapper;
  private final BroadcastProperties properties;

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public Msg91EmailAccountSnapshot inspect(
      BroadcastSenderConnection connection, String fromAddress) {
    requireProvider(connection);
    String authKey = authKey(connection);
    Msg91EmailProviderConfig config = config(connection);
    if (authKey.isBlank()) {
      throw new BadRequestException("MSG91 auth key is required");
    }

    String normalizedFrom = normalizeEmail(fromAddress);
    String fromDomain =
        normalizedFrom.substring(normalizedFrom.lastIndexOf('@') + 1);
    String configuredDomain = config.domain().toLowerCase(Locale.ROOT);
    if (!fromDomain.equals(configuredDomain)
        && !fromDomain.endsWith("." + configuredDomain)) {
      throw new BadRequestException(
          "MSG91 From email must belong to the configured sending domain");
    }

    return new Msg91EmailAccountSnapshot(
        true,
        Math.max(0.0, properties.getMsg91EmailMaxPerSecondCap()),
        normalizedFrom);
  }

  public BroadcastEmailResult send(
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastEmailCommand command) {
    requireProvider(connection);
    String authKey = authKey(connection);
    Msg91EmailProviderConfig config = config(connection);

    ObjectNode payload = objectMapper.createObjectNode();
    ObjectNode recipient = payload.putArray("recipients").addObject();
    ObjectNode to = recipient.putArray("to").addObject();
    to.put("email", command.recipient());
    to.put("CRQID", command.correlationId());

    ObjectNode variables = recipient.putObject("variables");
    variables.put(config.messageVariable(), command.bodyText());
    if (config.subjectVariable() != null
        && !config.subjectVariable().isBlank()
        && command.subject() != null) {
      variables.put(config.subjectVariable(), command.subject());
    }

    ObjectNode from = payload.putObject("from");
    from.put("email", sender.getFromAddress());
    if (sender.getDisplayName() != null && !sender.getDisplayName().isBlank()) {
      from.put("name", sender.getDisplayName());
    }

    payload.put("domain", config.domain());
    payload.put("template_id", config.templateId());

    if (sender.getReplyToAddress() != null && !sender.getReplyToAddress().isBlank()) {
      payload.putArray("reply_to").addObject().put("email", sender.getReplyToAddress());
    }

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

      if (status >= 200 && status < 300) {
        String providerMessageId = firstText(body, "request_id", "requestId", "message");
        return new BroadcastEmailResult(
            providerMessageId == null ? command.correlationId() : providerMessageId,
            null,
            Instant.now());
      }

      String reason =
          firstText(body, "message", "error", "error_message");
      if (reason == null) reason = "MSG91 rejected the email";
      if (status == 429 || status >= 500) {
        throw BroadcastTransportException.retryable(
            "MSG91_EMAIL_HTTP_" + status, reason, null);
      }
      throw BroadcastTransportException.permanent(
          "MSG91_EMAIL_HTTP_" + status, reason, null);
    } catch (BroadcastTransportException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw BroadcastTransportException.unknown(
          "MSG91_EMAIL_INTERRUPTED",
          "MSG91 Email send outcome could not be confirmed",
          exception);
    } catch (IOException exception) {
      throw BroadcastTransportException.unknown(
          "MSG91_EMAIL_SEND_UNKNOWN",
          "MSG91 Email send outcome could not be confirmed",
          exception);
    }
  }

  private Msg91EmailProviderConfig config(BroadcastSenderConnection connection) {
    if (connection.getProviderConfigJson() == null
        || connection.getProviderConfigJson().isBlank()) {
      throw new BadRequestException("MSG91 Email provider configuration is missing");
    }
    try {
      Msg91EmailProviderConfig config =
          objectMapper.readValue(
              connection.getProviderConfigJson(), Msg91EmailProviderConfig.class);
      if (isBlank(config.domain()) || isBlank(config.templateId())) {
        throw new BadRequestException(
            "MSG91 Email domain and template ID are required");
      }
      String messageVariable =
          isBlank(config.messageVariable()) ? "message" : config.messageVariable().trim();
      return new Msg91EmailProviderConfig(
          config.domain().trim().toLowerCase(Locale.ROOT),
          config.templateId().trim(),
          messageVariable,
          isBlank(config.subjectVariable()) ? null : config.subjectVariable().trim());
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Unable to read MSG91 Email provider configuration");
    }
  }

  private String authKey(BroadcastSenderConnection connection) {
    if (connection.getEncryptedCredentials() == null
        || connection.getEncryptedCredentials().isBlank()) {
      throw new BadRequestException("MSG91 Email auth key is missing");
    }
    try {
      String json = credentialCipher.decrypt(connection.getEncryptedCredentials());
      Msg91StoredCredentials credentials =
          objectMapper.readValue(json, Msg91StoredCredentials.class);
      if (credentials.authKey() == null || credentials.authKey().isBlank()) {
        throw new BadRequestException("MSG91 Email auth key is missing");
      }
      return credentials.authKey().trim();
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Unable to read MSG91 Email credentials");
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

  private String firstText(JsonNode body, String... fields) {
    for (String field : fields) {
      String value = body.path(field).asText("").trim();
      if (!value.isBlank()) return value;
    }
    return null;
  }

  private String normalizeEmail(String value) {
    if (value == null) throw new BadRequestException("MSG91 From email is required");
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    int at = normalized.lastIndexOf('@');
    if (at <= 0 || at == normalized.length() - 1) {
      throw new BadRequestException("MSG91 From email is invalid");
    }
    return normalized;
  }

  private void requireProvider(BroadcastSenderConnection connection) {
    if (connection.getProvider() != BroadcastSenderProvider.MSG91_EMAIL) {
      throw new IllegalArgumentException(
          "MSG91 Email gateway cannot handle provider " + connection.getProvider());
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}

package com.messaging.broadcast.sendgrid;

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
import com.messaging.common.credential.CredentialCipher;
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
public class SendGridGateway {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  private final CredentialCipher credentialCipher;
  private final ObjectMapper objectMapper;
  private final BroadcastProperties properties;

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

  public SendGridAccountSnapshot inspect(
      BroadcastSenderConnection connection, String fromAddress) {
    requireProvider(connection);
    String apiKey = apiKey(connection);
    SendGridRegion region = SendGridRegion.fromStoredValue(connection.getRegion());
    String normalized = normalizeEmail(fromAddress);

    JsonNode verifiedSenders =
        getForInspection(region, apiKey, "/v3/verified_senders?limit=100");
    String identity = verifiedSingleSender(verifiedSenders, normalized);
    if (identity == null) {
      JsonNode domains =
          getForInspection(region, apiKey, "/v3/whitelabel/domains?limit=100&offset=0");
      identity = verifiedDomain(domains, normalized);
    }
    if (identity == null) {
      throw new BadRequestException(
          "SendGrid sender is not verified. Verify the email address or authenticate its domain in SendGrid first.");
    }

    return new SendGridAccountSnapshot(
        true, Math.max(0.0, properties.getSendGridMaxPerSecondCap()), identity);
  }

  public BroadcastEmailResult send(
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastEmailCommand command) {
    requireProvider(connection);
    String apiKey = apiKey(connection);
    SendGridRegion region = SendGridRegion.fromStoredValue(connection.getRegion());

    ObjectNode payload = buildPayload(sender, command);

    HttpRequest request =
        requestBuilder(region, apiKey, "/v3/mail/send")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      if (status == 202) {
        String providerMessageId =
            response.headers().firstValue("X-Message-Id").orElse(null);
        return new BroadcastEmailResult(providerMessageId, null, Instant.now());
      }

      String message = responseMessage(response.body(), "SendGrid rejected the email");
      if (status == 429 || status >= 500) {
        throw BroadcastTransportException.retryable(
            "SENDGRID_HTTP_" + status, message, null);
      }
      throw BroadcastTransportException.permanent(
          "SENDGRID_HTTP_" + status, message, null);
    } catch (BroadcastTransportException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw BroadcastTransportException.unknown(
          "SENDGRID_INTERRUPTED",
          "SendGrid send outcome could not be confirmed",
          exception);
    } catch (IOException exception) {
      throw BroadcastTransportException.unknown(
          "SENDGRID_SEND_UNKNOWN",
          "SendGrid send outcome could not be confirmed",
          exception);
    }
  }

  ObjectNode buildPayload(BroadcastSender sender, BroadcastEmailCommand command) {
    ObjectNode payload = objectMapper.createObjectNode();
    var personalizations = payload.putArray("personalizations");
    ObjectNode personalization = personalizations.addObject();
    personalization.putArray("to").addObject().put("email", command.recipient());
    personalization
        .putObject("custom_args")
        .put("messaging_correlation_id", command.correlationId());

    ObjectNode from = payload.putObject("from");
    from.put("email", sender.getFromAddress());
    if (sender.getDisplayName() != null && !sender.getDisplayName().isBlank()) {
      from.put("name", sender.getDisplayName());
    }

    if (sender.getReplyToAddress() != null && !sender.getReplyToAddress().isBlank()) {
      payload.putObject("reply_to").put("email", sender.getReplyToAddress());
    }

    payload.put("subject", command.subject() == null ? "" : command.subject());
    var content = payload.putArray("content");
    content.addObject().put("type", "text/plain").put("value", command.bodyText());
    if (command.bodyHtml() != null && !command.bodyHtml().isBlank()) {
      content.addObject().put("type", "text/html").put("value", command.bodyHtml());
    }
    if (command.compliance() != null) {
      ObjectNode headers = payload.putObject("headers");
      command.compliance().standardOneClickHeaders().forEach(headers::put);
    }
    return payload;
  }

  private JsonNode getForInspection(
      SendGridRegion region, String apiKey, String path) {
    HttpRequest request = requestBuilder(region, apiKey, path).GET().build();
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return response.body() == null || response.body().isBlank()
            ? objectMapper.createObjectNode()
            : objectMapper.readTree(response.body());
      }
      if (response.statusCode() == 401 || response.statusCode() == 403) {
        throw new BadRequestException(
            "SendGrid API key is invalid or is missing Sender Authentication read permission");
      }
      throw new BadRequestException(
          "Unable to validate SendGrid account: "
              + responseMessage(response.body(), "HTTP " + response.statusCode()));
    } catch (BadRequestException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BadRequestException("SendGrid validation was interrupted");
    } catch (Exception exception) {
      throw new BadRequestException(
          "Unable to validate SendGrid account: " + safeMessage(exception));
    }
  }

  private HttpRequest.Builder requestBuilder(
      SendGridRegion region, String apiKey, String path) {
    return HttpRequest.newBuilder(URI.create(region.baseUrl() + path))
        .timeout(REQUEST_TIMEOUT)
        .header("Authorization", "Bearer " + apiKey)
        .header("Accept", "application/json");
  }

  private String verifiedSingleSender(JsonNode root, String fromAddress) {
    JsonNode results = root.path("results");
    if (!results.isArray() && root.isArray()) results = root;
    if (!results.isArray()) return null;

    for (JsonNode sender : results) {
      String email = sender.path("from_email").asText("").trim().toLowerCase(Locale.ROOT);
      if (fromAddress.equals(email) && sender.path("verified").asBoolean(false)) {
        return fromAddress;
      }
    }
    return null;
  }

  private String verifiedDomain(JsonNode root, String fromAddress) {
    if (!root.isArray()) return null;
    int at = fromAddress.lastIndexOf('@');
    if (at < 0 || at + 1 >= fromAddress.length()) return null;
    String fromDomain = fromAddress.substring(at + 1);

    for (JsonNode item : root) {
      if (!item.path("valid").asBoolean(false)) continue;
      String domain = item.path("domain").asText("").trim().toLowerCase(Locale.ROOT);
      if (domain.isBlank()) continue;
      if (fromDomain.equals(domain) || fromDomain.endsWith("." + domain)) {
        return domain;
      }
    }
    return null;
  }

  private String apiKey(BroadcastSenderConnection connection) {
    if (connection.getEncryptedCredentials() == null
        || connection.getEncryptedCredentials().isBlank()) {
      throw new BadRequestException("SendGrid credentials are missing");
    }
    try {
      String decrypted = credentialCipher.decrypt(connection.getEncryptedCredentials());
      SendGridStoredCredentials credentials =
          objectMapper.readValue(decrypted, SendGridStoredCredentials.class);
      if (credentials.apiKey() == null || credentials.apiKey().isBlank()) {
        throw new BadRequestException("SendGrid API key is missing");
      }
      return credentials.apiKey().trim();
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Unable to read SendGrid credentials");
    }
  }

  private void requireProvider(BroadcastSenderConnection connection) {
    if (connection.getProvider() != BroadcastSenderProvider.SENDGRID) {
      throw new IllegalArgumentException(
          "SendGrid gateway cannot handle provider " + connection.getProvider());
    }
  }

  private String normalizeEmail(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private String responseMessage(String body, String fallback) {
    if (body == null || body.isBlank()) return fallback;
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode errors = root.path("errors");
      if (errors.isArray() && !errors.isEmpty()) {
        String message = errors.get(0).path("message").asText("");
        if (!message.isBlank()) return message;
      }
      String message = root.path("message").asText("");
      return message.isBlank() ? fallback : message;
    } catch (Exception ignored) {
      String trimmed = body.trim();
      return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
    }
  }

  private String safeMessage(Throwable throwable) {
    String value = throwable.getMessage();
    return value == null || value.isBlank() ? throwable.getClass().getSimpleName() : value;
  }
}

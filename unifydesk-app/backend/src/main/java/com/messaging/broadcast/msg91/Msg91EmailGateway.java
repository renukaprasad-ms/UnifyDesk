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
import com.messaging.common.credential.CredentialCipher;
import com.messaging.common.exception.BadRequestException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Msg91EmailGateway {

  private static final URI SEND_URI =
      URI.create("https://control.msg91.com/api/v5/email/send");
  private static final URI TEMPLATES_URI =
      URI.create("https://control.msg91.com/api/v5/email/templates");
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

    String normalizedFrom = normalizeEmail(fromAddress);
    String fromDomain =
        normalizedFrom.substring(normalizedFrom.lastIndexOf('@') + 1);
    String configuredDomain = config.domain().toLowerCase(Locale.ROOT);
    if (!fromDomain.equals(configuredDomain)
        && !fromDomain.endsWith("." + configuredDomain)) {
      throw new BadRequestException(
          "MSG91 From email must belong to the configured sending domain");
    }

    verifyCredentials(authKey);

    return new Msg91EmailAccountSnapshot(
        true,
        Math.max(0.0, properties.getMsg91EmailMaxPerSecondCap()),
        normalizedFrom);
  }

  public Msg91EmailTemplateSnapshot createTemplate(
      BroadcastSenderConnection connection,
      String name,
      String slug,
      String subject,
      String html) {
    requireProvider(connection);
    String authKey = authKey(connection);

    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("name", name);
    payload.put("slug", slug);
    payload.put("subject", subject == null ? "" : subject);
    payload.put("body", html);

    HttpResponse<String> response =
        execute(
            HttpRequest.newBuilder(TEMPLATES_URI)
                .timeout(REQUEST_TIMEOUT)
                .header("accept", "application/json")
                .header("authkey", authKey)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build(),
            "MSG91 Email template sync failed");

    JsonNode body = parse(response.body());
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      String providerTemplateId =
          firstDeepText(body, "template_id", "templateId", "slug", "id", "_id");
      String providerSlug = firstDeepText(body, "slug", "template_id", "templateId");
      String providerStatus =
          firstDeepText(body, "status", "template_status", "approval_status");
      if (providerTemplateId == null) providerTemplateId = slug;
      if (providerSlug == null) providerSlug = slug;
      return new Msg91EmailTemplateSnapshot(
          providerTemplateId,
          providerSlug,
          providerStatus,
          Msg91EmailTemplateStatusMapper.map(providerStatus));
    }

    String reason = failureReason(body, response.statusCode());
    if (looksLikeDuplicate(reason)) {
      Msg91EmailTemplateSnapshot existing = findTemplate(connection, slug, slug);
      if (existing != null) return existing;
      return new Msg91EmailTemplateSnapshot(
          slug,
          slug,
          "EXISTS_UNVERIFIED",
          Msg91EmailTemplateStatusMapper.map("EXISTS_UNVERIFIED"));
    }
    throw new BadRequestException(reason);
  }

  public Msg91EmailTemplateSnapshot findTemplate(
      BroadcastSenderConnection connection,
      String providerTemplateId,
      String slug) {
    requireProvider(connection);
    String authKey = authKey(connection);

    HttpResponse<String> response =
        execute(
            HttpRequest.newBuilder(TEMPLATES_URI)
                .timeout(REQUEST_TIMEOUT)
                .header("accept", "application/json")
                .header("authkey", authKey)
                .GET()
                .build(),
            "Unable to read MSG91 Email templates");

    JsonNode body = parse(response.body());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new BadRequestException(failureReason(body, response.statusCode()));
    }

    LinkedHashSet<String> candidates = new LinkedHashSet<>();
    String providerIdValue = value(providerTemplateId);
    String slugValue = value(slug);
    if (providerIdValue != null) candidates.add(providerIdValue);
    if (slugValue != null) candidates.add(slugValue);
    if (candidates.isEmpty()) return null;

    JsonNode match = findTemplateNode(body, candidates);
    if (match == null) return null;

    String resolvedId =
        firstText(match, "template_id", "templateId", "slug", "id", "_id");
    String resolvedSlug =
        firstText(match, "slug", "template_id", "templateId");
    String providerStatus =
        firstDeepText(
            match,
            "status",
            "template_status",
            "approval_status",
            "version_status",
            "templateStatus",
            "approvalStatus");

    if (resolvedId == null) resolvedId = providerTemplateId == null ? slug : providerTemplateId;
    if (resolvedSlug == null) resolvedSlug = slug;

    return new Msg91EmailTemplateSnapshot(
        resolvedId,
        resolvedSlug,
        providerStatus,
        Msg91EmailTemplateStatusMapper.map(providerStatus));
  }

  public BroadcastEmailResult send(
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastEmailCommand command) {
    requireProvider(connection);
    String authKey = authKey(connection);
    Msg91EmailProviderConfig config = config(connection);

    if (command.providerTemplateId() == null || command.providerTemplateId().isBlank()) {
      throw BroadcastTransportException.permanent(
          "MSG91_EMAIL_TEMPLATE_REQUIRED",
          "MSG91 Email requires a synced provider template",
          null);
    }

    ObjectNode payload = buildSendPayload(sender, command, config);

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
        String providerMessageId = firstDeepText(body, "request_id", "requestId", "message");
        return new BroadcastEmailResult(
            providerMessageId == null ? command.correlationId() : providerMessageId,
            null,
            Instant.now());
      }

      String reason = failureReason(body, status);
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

  ObjectNode buildSendPayload(
      BroadcastSender sender,
      BroadcastEmailCommand command,
      Msg91EmailProviderConfig config) {
    ObjectNode payload = objectMapper.createObjectNode();
    ObjectNode recipient = payload.putArray("recipients").addObject();
    ObjectNode to = recipient.putArray("to").addObject();
    to.put("email", command.recipient());
    if (command.correlationId() != null && !command.correlationId().isBlank()) {
      to.put("CRQID", command.correlationId());
    }

    ObjectNode variables = recipient.putObject("variables");
    Map<String, String> templateVariables =
        command.templateVariables() == null ? Map.of() : command.templateVariables();
    templateVariables.forEach(variables::put);

    ObjectNode from = payload.putObject("from");
    from.put("email", sender.getFromAddress());
    if (sender.getDisplayName() != null && !sender.getDisplayName().isBlank()) {
      from.put("name", sender.getDisplayName());
    }

    payload.put("domain", config.domain());
    payload.put("template_id", command.providerTemplateId());

    if (sender.getReplyToAddress() != null && !sender.getReplyToAddress().isBlank()) {
      payload.putArray("reply_to").addObject().put("email", sender.getReplyToAddress());
    }
    return payload;
  }

  private void verifyCredentials(String authKey) {
    HttpResponse<String> response =
        execute(
            HttpRequest.newBuilder(TEMPLATES_URI)
                .timeout(REQUEST_TIMEOUT)
                .header("accept", "application/json")
                .header("authkey", authKey)
                .GET()
                .build(),
            "Unable to validate MSG91 Email credentials");
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new BadRequestException(
          failureReason(parse(response.body()), response.statusCode()));
    }
  }

  private HttpResponse<String> execute(HttpRequest request, String failureMessage) {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BadRequestException(failureMessage + ": request interrupted");
    } catch (IOException exception) {
      throw new BadRequestException(failureMessage + ": provider could not be reached");
    }
  }

  private JsonNode parse(String body) {
    if (body == null || body.isBlank()) return objectMapper.createObjectNode();
    try {
      return objectMapper.readTree(body);
    } catch (IOException exception) {
      return objectMapper.createObjectNode().put("message", body);
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
      if (isBlank(config.domain())) {
        throw new BadRequestException("MSG91 Email domain is required");
      }
      return new Msg91EmailProviderConfig(
          config.domain().trim().toLowerCase(Locale.ROOT),
          null,
          null,
          null);
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

  private JsonNode findTemplateNode(JsonNode node, Set<String> candidates) {
    if (node == null || node.isNull()) return null;

    if (node.isObject()) {
      for (String field : new String[] {"template_id", "templateId", "slug", "id", "_id"}) {
        String candidate = value(node.path(field).asText(null));
        if (candidate != null && candidates.contains(candidate)) return node;
      }
      Iterator<JsonNode> children = node.elements();
      while (children.hasNext()) {
        JsonNode found = findTemplateNode(children.next(), candidates);
        if (found != null) return found;
      }
    } else if (node.isArray()) {
      for (JsonNode child : node) {
        JsonNode found = findTemplateNode(child, candidates);
        if (found != null) return found;
      }
    }
    return null;
  }

  private boolean looksLikeDuplicate(String value) {
    if (value == null) return false;
    String normalized = value.toLowerCase(Locale.ROOT);
    return normalized.contains("already")
        || normalized.contains("duplicate")
        || normalized.contains("exists");
  }

  private String failureReason(JsonNode body, int status) {
    String reason =
        firstDeepText(body, "message", "error_message", "error", "detail");
    return reason == null || reason.isBlank()
        ? "MSG91 Email request failed with HTTP " + status
        : reason;
  }

  private String firstText(JsonNode body, String... fields) {
    if (body == null || !body.isObject()) return null;
    for (String field : fields) {
      JsonNode node = body.get(field);
      if (node == null || node.isNull() || node.isContainerNode()) continue;
      String value = node.asText("").trim();
      if (!value.isBlank()) return value;
    }
    return null;
  }

  private String firstDeepText(JsonNode node, String... fields) {
    if (node == null || node.isNull()) return null;
    String direct = firstText(node, fields);
    if (direct != null) return direct;
    if (node.isContainerNode()) {
      Iterator<JsonNode> children = node.elements();
      while (children.hasNext()) {
        String nested = firstDeepText(children.next(), fields);
        if (nested != null) return nested;
      }
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

  private String value(String value) {
    return value == null || value.isBlank() ? null : value.trim();
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

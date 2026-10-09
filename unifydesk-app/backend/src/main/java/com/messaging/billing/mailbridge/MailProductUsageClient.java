package com.messaging.billing.mailbridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.ServiceUnavailableException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Private service-to-service Mail ledger reader. Never uses user-provided URLs.
 * The same bearer token is required by Mail's InternalApiTokenFilter.
 */
@Component
public class MailProductUsageClient {
  private final HttpClient http;
  private final ObjectMapper mapper;
  private final String baseUrl;
  private final String token;

  public record Handoff(long id, long recipientId, long messageId,
                        Instant occurredAt, long productPriceMicroInr) {}
  public record Page(long projectId, List<Handoff> entries, long nextAfterId, boolean hasMore) {}

  public MailProductUsageClient(
      @Value("${app.billing.mail-bridge.base-url:}") String baseUrl,
      @Value("${app.billing.mail-bridge.internal-token:}") String token,
      ObjectMapper mapper) {
    this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(3)).build(), baseUrl, token, mapper);
  }

  // Package-private constructor enables deterministic tests without network requests.
  MailProductUsageClient(HttpClient http, String baseUrl, String token, ObjectMapper mapper) {
    this.http = http;
    this.mapper = mapper;
    this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    this.token = token == null ? "" : token;
  }

  private void requireConfigured() {
    if (token.length() < 32 || baseUrl.isBlank()) {
      throw new ServiceUnavailableException("Mail billing bridge is not configured");
    }
    URI url;
    try {
      url = URI.create(baseUrl);
    } catch (IllegalArgumentException exception) {
      throw new ServiceUnavailableException("Invalid Mail billing bridge endpoint");
    }
    String host = url.getHost();
    boolean trustedHttpHost = host != null && (host.equals("localhost")
        || host.equals("127.0.0.1") || host.endsWith(".svc")
        || host.endsWith(".svc.cluster.local"));
    if (host == null || url.getUserInfo() != null || url.getQuery() != null
        || url.getFragment() != null
        || !("https".equalsIgnoreCase(url.getScheme())
            || ("http".equalsIgnoreCase(url.getScheme()) && trustedHttpHost))) {
      throw new ServiceUnavailableException("Mail billing bridge requires HTTPS or internal Kubernetes DNS");
    }
  }

  private JsonNode get(Long projectId, String suffix) {
    requireConfigured();
    if (projectId == null || projectId <= 0) {
      throw new IllegalArgumentException("Positive Mail project ID required");
    }
    URI uri = URI.create(baseUrl + "/internal/v1/email-product/projects/" + projectId
        + "/billing/" + suffix);
    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(8))
        .header("Authorization", "Bearer " + token)
        .header("Accept", "application/json")
        .GET().build();
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new ServiceUnavailableException(
            "Mail usage query failed with status " + response.statusCode());
      }
      JsonNode payload = mapper.readTree(response.body());
      if (!payload.path("status").asBoolean(false) || payload.path("data").isMissingNode()) {
        throw new ServiceUnavailableException("Mail usage API returned an invalid response");
      }
      return payload.get("data");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ServiceUnavailableException("Mail usage query interrupted", exception);
    } catch (IOException exception) {
      throw new ServiceUnavailableException("Mail usage endpoint unavailable", exception);
    } catch (IllegalArgumentException exception) {
      throw new ServiceUnavailableException("Mail usage endpoint or response is invalid", exception);
    }
  }

  public long head(Long projectId) {
    JsonNode data = get(projectId, "usage-head");
    try {
      long value = Long.parseLong(data.asText());
      if (value < 0) throw new NumberFormatException("negative cursor");
      return value;
    } catch (NumberFormatException exception) {
      throw new ServiceUnavailableException("Mail usage API returned an invalid head cursor", exception);
    }
  }

  public Page handoffs(Long projectId, long afterId, int limit) {
    if (afterId < 0 || limit < 1 || limit > 500) {
      throw new IllegalArgumentException("Invalid Mail usage cursor or page size");
    }
    return parsePage(projectId, afterId, limit, get(projectId,
        "usage-entries?afterId=" + afterId + "&limit=" + limit));
  }

  /** Bounded, idempotent recovery for out-of-order transaction commits. */
  public Page replay(Long projectId, long afterId, int limit, Instant from, Instant to) {
    if (afterId < 0 || limit < 1 || limit > 500 || from == null || to == null
        || !from.isBefore(to) || from.isBefore(to.minusSeconds(31L * 86400))
        || to.isAfter(Instant.now().plusSeconds(60))) {
      throw new IllegalArgumentException("Mail handoff replay requires a valid 31-day window");
    }
    String suffix = "usage-entries?afterId=" + afterId + "&limit=" + limit
        + "&from=" + java.net.URLEncoder.encode(from.toString(),
            java.nio.charset.StandardCharsets.UTF_8)
        + "&to=" + java.net.URLEncoder.encode(to.toString(),
            java.nio.charset.StandardCharsets.UTF_8);
    return parsePage(projectId, afterId, limit, get(projectId, suffix));
  }

  private Page parsePage(Long projectId, long afterId, int limit, JsonNode body) {
    long returnedProjectId;
    try {
      returnedProjectId = Long.parseLong(body.path("projectId").asText("-1"));
    } catch (NumberFormatException exception) {
      throw new ServiceUnavailableException("Mail usage API returned invalid project identity",
          exception);
    }
    if (returnedProjectId != projectId) {
      throw new ServiceUnavailableException("Mail project ID mismatch in usage response");
    }
    JsonNode rows = body.path("entries");
    if (!rows.isArray() || rows.size() > limit) {
      throw new ServiceUnavailableException("Mail usage response exceeded requested page");
    }
    List<Handoff> entries = new ArrayList<>();
    long last = afterId;
    for (JsonNode row : rows) {
      try {
        long id = Long.parseLong(row.path("usageId").asText("-1"));
        long recipient = Long.parseLong(row.path("recipientId").asText("-1"));
        long message = Long.parseLong(row.path("messageId").asText("-1"));
        Instant occurred = Instant.parse(row.path("occurredAt").asText());
        long micro = row.path("productPriceMicroInr").asLong(-1);
        if (id <= last || recipient <= 0 || message <= 0 || micro < 0
            || occurred.isAfter(Instant.now().plusSeconds(60))) {
          throw new IllegalArgumentException("Mail usage event violates integrity checks");
        }
        entries.add(new Handoff(id, recipient, message, occurred, micro));
        last = id;
      } catch (RuntimeException exception) {
        throw new ServiceUnavailableException("Mail usage source contains invalid records", exception);
      }
    }
    if (entries.isEmpty() && body.path("hasMore").asBoolean(false)) {
      throw new ServiceUnavailableException("Mail handoff source returned an empty continuing page");
    }
    try {
      long cursor = Long.parseLong(body.path("nextAfterId").asText("-1"));
      if (cursor != last) {
        throw new ServiceUnavailableException("Mail usage cursor does not match last record");
      }
      return new Page(projectId, List.copyOf(entries), cursor, body.path("hasMore").asBoolean());
    } catch (NumberFormatException exception) {
      throw new ServiceUnavailableException("Mail usage response contains invalid cursor", exception);
    }
  }
}

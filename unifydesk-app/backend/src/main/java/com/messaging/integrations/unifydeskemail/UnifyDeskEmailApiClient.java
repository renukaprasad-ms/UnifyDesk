package com.messaging.integrations.unifydeskemail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Independent-product API client. Each UnifyDesk workspace must supply its own
 * encrypted-at-rest email project key. No shared database access and no key logging.
 * This is deliberately NOT registered as a broadcast provider until billing/consent
 * and connection lifecycle contracts are implemented.
 */
@Component
public class UnifyDeskEmailApiClient {
  private final HttpClient http;
  private final ObjectMapper mapper;
  private final URI baseUrl;

  public UnifyDeskEmailApiClient(
      ObjectMapper mapper,
      @Value("${APP_UNIFYDESK_EMAIL_API_BASE_URL:https://api.email.unifydesk.in}") String base) {
    this.mapper = mapper;
    this.baseUrl = URI.create(base);
    boolean https = "https".equalsIgnoreCase(baseUrl.getScheme());
    boolean localDev = "http".equalsIgnoreCase(baseUrl.getScheme())
        && ("localhost".equalsIgnoreCase(baseUrl.getHost())
            || "127.0.0.1".equals(baseUrl.getHost()));
    if (!https && !localDev) {
      throw new IllegalArgumentException("UnifyDesk Email API requires HTTPS");
    }
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public record BatchRequest(
      Long sendingDomainId, String from, String replyTo, String subject,
      String text, String html, List<String> recipients, String consentReference) {}


  public record ProjectInfo(String projectId, boolean sendingEnabled) {}

  public ProjectInfo inspectProject(String projectApiKey) throws IOException, InterruptedException {
    if (projectApiKey == null || !projectApiKey.startsWith("ude_live_")) {
      throw new IllegalArgumentException("Project key required");
    }
    HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("/v1/project"))
        .timeout(Duration.ofSeconds(15))
        .header("Authorization", "Bearer " + projectApiKey).GET().build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("UnifyDesk Email project check HTTP " + response.statusCode());
    }
    JsonNode data = mapper.readTree(response.body()).path("data");
    if (!data.hasNonNull("projectId") || !data.has("sendingEnabled")) {
      throw new IOException("Invalid UnifyDesk Email project status response");
    }
    return new ProjectInfo(data.path("projectId").asText(), data.path("sendingEnabled").asBoolean());
  }

  public record DomainInfo(String id, String domain, String status) {}

  public DomainInfo inspectDomain(String projectApiKey, Long domainId)
      throws IOException, InterruptedException {
    if (domainId == null || domainId <= 0
        || projectApiKey == null || !projectApiKey.startsWith("ude_live_")) {
      throw new IllegalArgumentException("Project key and domain ID are required");
    }
    HttpRequest request = HttpRequest.newBuilder(
            baseUrl.resolve("/v1/domains/" + domainId))
        .timeout(Duration.ofSeconds(15))
        .header("Authorization", "Bearer " + projectApiKey)
        .GET().build();
    HttpResponse<String> response;
    try {
      response = http.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw interrupted;
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("UnifyDesk Email domain verification HTTP " + response.statusCode());
    }
    JsonNode data = mapper.readTree(response.body()).path("data");
    String name = data.path("domain").asText("");
    String status = data.path("status").asText("");
    if (!Long.toString(domainId).equals(data.path("id").asText())
        || name.isBlank() || status.isBlank()) {
      throw new IOException("UnifyDesk Email returned invalid domain details");
    }
    return new DomainInfo(data.path("id").asText(), name, status);
  }

  public record BatchSubmission(String batchId, int totalRecipients, int chunksCount) {}

  public BatchSubmission submit(
      String projectApiKey, String idempotencyKey, BatchRequest request)
      throws InterruptedException, IOException {
    if (projectApiKey == null || !projectApiKey.startsWith("ude_live_")
        || idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Valid project API key and idempotency key required");
    }
    HttpRequest httpRequest = HttpRequest.newBuilder(
            baseUrl.resolve("/v1/email-batches"))
        .timeout(Duration.ofSeconds(30))
        .header("Authorization", "Bearer " + projectApiKey)
        .header("Idempotency-Key", idempotencyKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(request)))
        .build();

    HttpResponse<String> response;
    try {
      response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw interrupted;
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("UnifyDesk Email rejected batch with HTTP " + response.statusCode());
    }
    JsonNode data = mapper.readTree(response.body()).path("data");
    if (data.path("batchId").isMissingNode() || data.path("batchId").asText().isBlank()) {
      throw new IOException("UnifyDesk Email accepted response without a batch identifier");
    }
    return new BatchSubmission(data.path("batchId").asText(),
        data.path("totalRecipients").asInt(), data.path("chunksCount").asInt());
  }
}

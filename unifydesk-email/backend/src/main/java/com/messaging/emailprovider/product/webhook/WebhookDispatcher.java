package com.messaging.emailprovider.product.webhook;

import com.messaging.emailprovider.common.credential.CredentialCipher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.product.webhooks", name = "enabled", havingValue = "true")
public class WebhookDispatcher {
  private final WebhookDeliveryRepository deliveries;
  private final WebhookEndpointRepository endpoints;
  private final CredentialCipher cipher;
  private final WebhookSigning signing;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public WebhookDispatcher(
      WebhookDeliveryRepository deliveries,
      WebhookEndpointRepository endpoints,
      CredentialCipher cipher,
      WebhookSigning signing) {
    this.deliveries = deliveries;
    this.endpoints = endpoints;
    this.cipher = cipher;
    this.signing = signing;
  }

  @Scheduled(fixedDelayString = "${app.product.webhooks.dispatch-interval-ms:10000}")
  public void dispatch() {
    Instant now = Instant.now();
    deliveries.recoverStale(now.minus(Duration.ofMinutes(10)), now);
    for (Long id : deliveries.dueIds(now)) {
      if (deliveries.claim(id, now) != 1) continue;
      dispatchOne(id);
    }
  }

  @Transactional
  public void dispatchOne(Long id) {
    WebhookDelivery job = deliveries.findById(id).orElse(null);
    if (job == null || !"PROCESSING".equals(job.getStatus())) return;
    WebhookEndpoint endpoint = endpoints.findById(job.getEndpointId()).orElse(null);
    if (endpoint == null || !endpoint.isEnabled()) {
      job.setStatus("DISABLED");
      deliveries.save(job);
      return;
    }
    try {
      // This worker is gated behind an operator-approved hostname allowlist at registration.
      String timestamp = Long.toString(Instant.now().getEpochSecond());
      String signature =
          signing.sign(cipher.decrypt(endpoint.getEncryptedSecret()), timestamp, job.getPayload());
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(endpoint.getEndpointUrl()))
              .timeout(Duration.ofSeconds(10))
              .header("Content-Type", "application/json")
              .header("X-UnifyDesk-Timestamp", timestamp)
              .header("X-UnifyDesk-Signature", signature)
              .header("X-UnifyDesk-Event-ID", job.getEventId().toString())
              .POST(HttpRequest.BodyPublishers.ofString(job.getPayload(), StandardCharsets.UTF_8))
              .build();
      HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        job.setStatus("DELIVERED");
        job.setDeliveredAt(Instant.now());
      } else if (response.statusCode() >= 400
          && response.statusCode() < 500
          && response.statusCode() != 408
          && response.statusCode() != 429) {
        job.setStatus("FAILED");
        job.setLastError("Webhook rejected with HTTP " + response.statusCode());
      } else {
        retry(job, "Webhook returned HTTP " + response.statusCode());
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      retry(job, "Webhook delivery interrupted");
    } catch (Exception error) {
      retry(job, "Webhook endpoint unreachable");
    }
    deliveries.save(job);
  }

  private void retry(WebhookDelivery job, String reason) {
    job.setLastError(reason);
    if (job.getAttempts() >= 10) {
      job.setStatus("FAILED");
      return;
    }
    job.setStatus("RETRY");
    long seconds = Math.min(21600, 30L * (1L << Math.min(job.getAttempts(), 10)));
    job.setNextAttemptAt(Instant.now().plusSeconds(seconds));
  }
}

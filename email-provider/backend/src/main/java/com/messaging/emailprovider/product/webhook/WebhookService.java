package com.messaging.emailprovider.product.webhook;

import com.messaging.emailprovider.common.credential.CredentialCipher;
import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.feedback.EmailEvent;
import com.messaging.emailprovider.feedback.LifecycleEventEncoder;
import com.messaging.emailprovider.mail.entity.EmailMessage;
import com.messaging.emailprovider.mail.entity.EmailRecipient;
import com.messaging.emailprovider.product.auth.ApiKeyCrypto;
import com.messaging.emailprovider.product.repo.EmailProjectRepository;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {
  private final WebhookEndpointRepository endpoints;
  private final WebhookDeliveryRepository deliveries;
  private final EmailProjectRepository projects;
  private final CredentialCipher cipher;
  private final LifecycleEventEncoder encoder;
  private final List<String> allowedHosts;

  public WebhookService(WebhookEndpointRepository endpoints, WebhookDeliveryRepository deliveries,
      EmailProjectRepository projects, CredentialCipher cipher, LifecycleEventEncoder encoder,
      @Value("${app.product.webhooks.allowed-hosts:}") String allowlist) {
    this.endpoints = endpoints;
    this.deliveries = deliveries;
    this.projects = projects;
    this.cipher = cipher;
    this.encoder = encoder;
    this.allowedHosts = Arrays.stream(allowlist.split(","))
        .map(x -> x.trim().toLowerCase(Locale.ROOT))
        .filter(x -> !x.isBlank()).toList();
  }

  public record View(String id, String url, boolean enabled) {}
  public record Created(View endpoint, String signingSecret) {}

  @Transactional
  public Created create(Long projectId, String url) {
    if (!projects.existsById(projectId)) throw new NotFoundException("Project not found");
    URI target;
    try { target = URI.create(url); }
    catch (RuntimeException badUrl) { throw new BadRequestException("Invalid webhook URL"); }
    String host = target.getHost() == null ? "" : target.getHost().toLowerCase(Locale.ROOT);
    if (!"https".equalsIgnoreCase(target.getScheme()) || target.getUserInfo() != null
        || target.getFragment() != null || target.getPort() != -1
        || !allowedHosts.contains(host) || url.length() > 1024) {
      throw new BadRequestException("HTTPS webhook URL must match an operator-approved host");
    }
    String secret = "udwh_" + ApiKeyCrypto.random(32);
    WebhookEndpoint endpoint = new WebhookEndpoint();
    endpoint.setProjectId(projectId);
    endpoint.setEndpointUrl(url);
    endpoint.setEncryptedSecret(cipher.encrypt(secret));
    endpoint = endpoints.save(endpoint);
    return new Created(view(endpoint), secret);
  }

  @Transactional(readOnly = true)
  public List<View> list(Long projectId) {
    return endpoints.findAllByProjectIdOrderByCreatedAtDesc(projectId).stream()
        .map(this::view).toList();
  }

  @Transactional
  public void disable(Long projectId, Long endpointId) {
    WebhookEndpoint endpoint = endpoints.findByIdAndProjectId(endpointId, projectId)
        .orElseThrow(() -> new NotFoundException("Webhook endpoint not found"));
    endpoint.setEnabled(false);
    endpoints.save(endpoint);
  }

  // Called in the SAME transaction as delivery feedback persistence.
  @Transactional
  public void enqueue(EmailEvent event, EmailMessage message, EmailRecipient recipient) {
    if (!projects.existsById(event.getCompanyId())) return; // legacy non-product route
    String payload = encoder.encode(message, recipient, event.getEventType(),
        event.getOccurredAt(), event.getSmtpStatusCode(), event.getDiagnostic());
    for (WebhookEndpoint endpoint : endpoints.findAllByProjectIdAndEnabledTrue(event.getCompanyId())) {
      WebhookDelivery delivery = new WebhookDelivery();
      delivery.setEndpointId(endpoint.getId());
      delivery.setEventId(event.getId());
      delivery.setPayload(payload);
      deliveries.save(delivery);
    }
  }

  private View view(WebhookEndpoint e) {
    return new View(e.getId().toString(), e.getEndpointUrl(), e.isEnabled());
  }
}

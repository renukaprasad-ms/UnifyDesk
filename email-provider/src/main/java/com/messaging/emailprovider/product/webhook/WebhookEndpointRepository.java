package com.messaging.emailprovider.product.webhook;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {
  List<WebhookEndpoint> findAllByProjectIdAndEnabledTrue(Long projectId);
  List<WebhookEndpoint> findAllByProjectIdOrderByCreatedAtDesc(Long projectId);
  Optional<WebhookEndpoint> findByIdAndProjectId(Long id, Long projectId);
}

package com.messaging.emailprovider.product.webhook;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @Entity
@Table(name = "email_webhook_endpoints")
public class WebhookEndpoint extends BaseEntity {
  @Column(name = "project_id", nullable = false)
  private Long projectId;
  @Column(name = "endpoint_url", nullable = false, length = 1024)
  private String endpointUrl;
  @Column(name = "encrypted_secret", nullable = false, columnDefinition = "TEXT")
  private String encryptedSecret;
  @Column(nullable = false)
  private boolean enabled = true;
}

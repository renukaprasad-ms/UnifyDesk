package com.messaging.observability.webhook;

import com.messaging.channel.enums.ProviderType;
import com.messaging.common.id.IdGenerator;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "webhook_callbacks",
    indexes = {
      @Index(name = "idx_webhook_callbacks_received_at", columnList = "received_at"),
      @Index(
          name = "idx_webhook_callbacks_provider_status_received",
          columnList = "provider_type,status,received_at"),
      @Index(name = "idx_webhook_callbacks_event_id", columnList = "event_id")
    })
public class WebhookCallback {

  @Id private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider_type", nullable = false, length = 40)
  private ProviderType providerType;

  @Column(name = "callback_type", nullable = false, length = 80)
  private String callbackType;

  @Column(nullable = false, length = 255)
  private String endpoint;

  @Column(name = "http_method", nullable = false, length = 12)
  private String httpMethod;

  @Column(name = "query_string", columnDefinition = "text")
  private String queryString;

  @Column(name = "remote_address", length = 128)
  private String remoteAddress;

  @Column(name = "content_type", length = 160)
  private String contentType;

  @Column(name = "event_id", length = 255)
  private String eventId;

  @Column(name = "request_headers", columnDefinition = "text")
  private String requestHeaders;

  @Column(columnDefinition = "text")
  private String payload;

  @Column(name = "decoded_payload", columnDefinition = "text")
  private String decodedPayload;

  @Column(name = "processing_result", columnDefinition = "text")
  private String processingResult;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private WebhookCallbackStatus status;

  @Column(name = "http_status")
  private Integer httpStatus;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "duration_ms")
  private Long durationMs;

  @PrePersist
  private void beforeInsert() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
    if (status == null) {
      status = WebhookCallbackStatus.RECEIVED;
    }
    if (receivedAt == null) {
      receivedAt = Instant.now();
    }
  }
}

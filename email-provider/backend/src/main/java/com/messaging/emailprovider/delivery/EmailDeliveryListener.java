package com.messaging.emailprovider.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "app",
    name = {"kafka.enabled", "delivery.enabled"},
    havingValue = "true")
public class EmailDeliveryListener {

  private final EmailDeliveryWorkerService worker;

  public EmailDeliveryListener(EmailDeliveryWorkerService worker) {
    this.worker = worker;
  }

  @KafkaListener(topics = "${app.kafka.topics.delivery-requested:email.delivery.requested}")
  public void onDeliveryRequested(String messageId) {
    try {
      worker.deliverMessage(Long.parseLong(messageId));
    } catch (NumberFormatException exception) {
      log.error("invalid_delivery_event payload={}", messageId);
    }
  }
}

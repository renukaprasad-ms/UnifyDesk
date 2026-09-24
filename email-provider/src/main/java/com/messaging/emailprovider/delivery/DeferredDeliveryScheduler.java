package com.messaging.emailprovider.delivery;

import com.messaging.emailprovider.mail.repository.EmailRecipientRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.delivery", name = "enabled", havingValue = "true")
public class DeferredDeliveryScheduler {

  private final EmailRecipientRepository recipientRepository;
  private final RecipientDeliveryStateService stateService;
  private final EmailDeliveryWorkerService worker;
  private final int staleAfterSeconds;

  public DeferredDeliveryScheduler(
      EmailRecipientRepository recipientRepository,
      RecipientDeliveryStateService stateService,
      EmailDeliveryWorkerService worker,
      @Value("${app.delivery.processing-stale-after-seconds:300}") int staleAfterSeconds) {
    this.recipientRepository = recipientRepository;
    this.stateService = stateService;
    this.worker = worker;
    this.staleAfterSeconds = staleAfterSeconds;
  }

  @Scheduled(fixedDelayString = "${app.delivery.retry.poll-interval-ms:1000}")
  public void retryDueDeliveries() {
    stateService.recoverStale(staleAfterSeconds);
    for (Long messageId : recipientRepository.findMessageIdsWithDueRetries(Instant.now())) {
      worker.deliverMessage(messageId);
    }
  }
}

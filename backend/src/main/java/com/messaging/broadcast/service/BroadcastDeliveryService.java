package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.transport.*;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastDeliveryService {

  private final BroadcastEmailTransportRegistry transportRegistry;
  private final BroadcastRateLimiter rateLimiter;
  private final BroadcastRetryPolicy retryPolicy;
  private final BroadcastDeliveryStateService stateService;

  public void deliver(
      BroadcastRun run,
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastRecipient recipient) {
    if (!rateLimiter.tryAcquire(connection)) {
      stateService.releaseForRateLimit(recipient.getId(), Instant.now().plusSeconds(1));
      return;
    }

    BroadcastDeliveryStateService.AttemptContext attempt =
        stateService.begin(recipient.getId(), run.getProviderType());
    BroadcastEmailCommand command =
        new BroadcastEmailCommand(
            recipient.getDestination(),
            run.getSubjectSnapshot(),
            run.getBodyTextSnapshot(),
            recipient.getInternetMessageId(),
            recipient.getProviderCorrelationId());

    try {
      BroadcastEmailResult result =
          transportRegistry.require(run.getProviderType()).send(connection, sender, command);
      stateService.accepted(attempt, result);
    } catch (BroadcastTransportException exception) {
      switch (exception.outcome()) {
        case RETRYABLE -> {
          if (retryPolicy.canRetry(attempt.attemptNo())) {
            stateService.retry(
                attempt,
                exception.code(),
                exception.getMessage(),
                retryPolicy.nextAttempt(attempt.attemptNo()));
          } else {
            stateService.failed(attempt, "RETRY_EXHAUSTED", exception.getMessage());
          }
        }
        case PERMANENT ->
            stateService.failed(attempt, exception.code(), exception.getMessage());
        case UNKNOWN ->
            stateService.unknown(attempt, exception.code(), exception.getMessage());
      }
    } catch (RuntimeException exception) {
      stateService.unknown(
          attempt,
          "UNEXPECTED_SEND_OUTCOME",
          exception.getMessage() == null
              ? exception.getClass().getSimpleName()
              : exception.getMessage());
    }
  }
}

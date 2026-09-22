package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.transport.*;
import com.messaging.channel.enums.ChannelType;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastDeliveryService {

  private final BroadcastEmailTransportRegistry emailTransportRegistry;
  private final BroadcastSmsTransportRegistry smsTransportRegistry;
  private final BroadcastRateLimiter rateLimiter;
  private final BroadcastRetryPolicy retryPolicy;
  private final BroadcastDeliveryStateService stateService;
  private final BroadcastTemplateRenderer templateRenderer;

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

    try {
      ChannelType channelType = run.getProviderType().channelType();
      BroadcastTemplateRenderer.RenderedContent rendered =
          templateRenderer.render(
              run.getSubjectSnapshot(),
              run.getBodyTextSnapshot(),
              recipient,
              channelType);

      if (!rendered.unresolvedVariables().isEmpty()) {
        stateService.failed(
            attempt,
            "MISSING_TEMPLATE_VARIABLE",
            "Missing template variables: "
                + String.join(", ", rendered.unresolvedVariables()));
        return;
      }

      if (channelType == ChannelType.SMS) {
        BroadcastSmsResult result =
            smsTransportRegistry
                .require(run.getProviderType())
                .send(
                    connection,
                    sender,
                    new BroadcastSmsCommand(
                        recipient.getDestination(),
                        rendered.bodyText(),
                        recipient.getProviderCorrelationId()));
        stateService.accepted(
            attempt, result.providerMessageId(), null, result.acceptedAt());
      } else {
        BroadcastEmailResult result =
            emailTransportRegistry
                .require(run.getProviderType())
                .send(
                    connection,
                    sender,
                    new BroadcastEmailCommand(
                        recipient.getDestination(),
                        rendered.subject(),
                        rendered.bodyText(),
                        recipient.getInternetMessageId(),
                        recipient.getProviderCorrelationId()));
        stateService.accepted(attempt, result);
      }
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

package com.messaging.broadcast.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.channel.meta.runtime.MetaWhatsAppControlPlaneProjectionRepository;
import com.messaging.channel.meta.runtime.MetaWhatsAppCredentialResolver;
import com.messaging.channel.meta.runtime.MetaWhatsAppOutboundRateLimiter;
import com.messaging.channel.meta.runtime.MetaWhatsAppProviderException;
import com.messaging.channel.meta.runtime.MetaWhatsAppRuntimeGraphClient;
import com.messaging.channel.meta.runtime.MetaWhatsAppSendFailurePolicy;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.exception.TooManyRequestsException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastWhatsAppDeliveryService {

  private final MetaWhatsAppControlPlaneProjectionRepository projectionRepository;
  private final MetaWhatsAppCredentialResolver credentialResolver;
  private final MetaWhatsAppRuntimeGraphClient graphClient;
  private final MetaWhatsAppOutboundRateLimiter outboundRateLimiter;
  private final MetaWhatsAppSendFailurePolicy sendFailurePolicy;
  private final BroadcastDeliveryStateService stateService;
  private final BroadcastRetryPolicy retryPolicy;
  private final ObjectMapper objectMapper;

  public void deliver(
      BroadcastRun run,
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastRecipient recipient) {
    if (connection.getChannelConnectionId() == null
        || run.getWhatsAppTemplateNameSnapshot() == null
        || run.getWhatsAppTemplateLanguageSnapshot() == null) {
      stateService.skipProcessing(
          recipient.getId(),
          "WHATSAPP_SENDER_ACTION_REQUIRED",
          "WhatsApp Broadcast sender or template is not ready");
      return;
    }

    MetaWhatsAppControlPlaneProjectionRepository.Projection projection =
        projectionRepository
            .findByConnectionId(connection.getChannelConnectionId())
            .filter(value -> value.companyId() == run.getCompanyId())
            .orElse(null);
    if (projection == null
        || !projection.usableForSend()
        || !Objects.equals(projection.selectedPhoneNumberId(), sender.getVerifiedIdentity())) {
      stateService.skipProcessing(
          recipient.getId(),
          "WHATSAPP_SENDER_ACTION_REQUIRED",
          "WhatsApp sender requires attention before delivery");
      return;
    }

    MetaWhatsAppCredentialResolver.StoredCredential credential;
    List<Map<String, Object>> components;
    String destination;
    try {
      credential = credentialResolver.require(connection.getChannelConnectionId());
      if (!Objects.equals(credential.phoneNumberId(), projection.selectedPhoneNumberId())) {
        stateService.skipProcessing(
            recipient.getId(),
            "WHATSAPP_SENDER_ACTION_REQUIRED",
            "WhatsApp sender credentials no longer match the selected phone");
        return;
      }
      components = components(run.getWhatsAppTemplateComponentsJsonSnapshot());
      destination = providerDestination(recipient.getNormalizedDestination());
      outboundRateLimiter.requirePermit(credential.phoneNumberId());
    } catch (TooManyRequestsException exception) {
      stateService.releaseForRateLimit(recipient.getId(), Instant.now().plusSeconds(1));
      return;
    } catch (ServiceUnavailableException exception) {
      stateService.releaseForRateLimit(recipient.getId(), Instant.now().plusSeconds(30));
      return;
    } catch (RuntimeException exception) {
      stateService.skipProcessing(
          recipient.getId(),
          "WHATSAPP_TEMPLATE_INVALID",
          "WhatsApp template snapshot is invalid");
      return;
    }

    BroadcastDeliveryStateService.AttemptContext attempt =
        stateService.begin(recipient.getId(), run.getProviderType());
    try {
      MetaWhatsAppRuntimeGraphClient.SendResult result =
          graphClient.sendTemplate(
              credential,
              destination,
              run.getWhatsAppTemplateNameSnapshot(),
              run.getWhatsAppTemplateLanguageSnapshot(),
              components);
      stateService.accepted(attempt, result.providerMessageId(), null, Instant.now());
    } catch (MetaWhatsAppProviderException exception) {
      if (Integer.valueOf(429).equals(exception.httpStatus())) {
        try {
          outboundRateLimiter.imposeProviderCooldown(
              credential.phoneNumberId(), exception.retryAfter());
        } catch (RuntimeException ignored) {
          // The delivery outcome below remains authoritative even if cooldown persistence fails.
        }
      }
      handleProviderFailure(attempt, exception);
    } catch (RuntimeException exception) {
      stateService.unknown(
          attempt, "WHATSAPP_SEND_AMBIGUOUS", "WhatsApp provider outcome is uncertain");
    }
  }

  private void handleProviderFailure(
      BroadcastDeliveryStateService.AttemptContext attempt,
      MetaWhatsAppProviderException exception) {
    MetaWhatsAppSendFailurePolicy.Outcome outcome = sendFailurePolicy.outcome(exception);
    switch (outcome) {
      case UNKNOWN ->
          stateService.unknown(
              attempt, "WHATSAPP_SEND_AMBIGUOUS", "WhatsApp provider outcome is uncertain");
      case REJECTED ->
          stateService.failed(attempt, exception.safeCategory(), exception.safeCategory());
      case RETRYABLE_FAILURE -> {
        if (!retryPolicy.canRetry(attempt.attemptNo())) {
          stateService.failed(attempt, "RETRY_EXHAUSTED", exception.safeCategory());
          return;
        }
        Duration retryAfter = exception.retryAfter();
        Instant next =
            retryAfter != null && !retryAfter.isNegative() && !retryAfter.isZero()
                ? Instant.now().plus(retryAfter)
                : retryPolicy.nextAttempt(attempt.attemptNo());
        stateService.retry(
            attempt, exception.safeCategory(), exception.safeCategory(), next);
      }
    }
  }

  private List<Map<String, Object>> components(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      List<Map<String, Object>> value =
          objectMapper.readValue(
              json, new TypeReference<List<Map<String, Object>>>() {});
      return value == null ? List.of() : List.copyOf(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Stored WhatsApp template snapshot is invalid", exception);
    }
  }

  private String providerDestination(String normalizedDestination) {
    if (normalizedDestination == null) throw new IllegalArgumentException("Missing destination");
    String value = normalizedDestination.trim();
    if (value.startsWith("+")) value = value.substring(1);
    if (!value.matches("[0-9]{6,20}")) {
      throw new IllegalArgumentException("Invalid WhatsApp destination");
    }
    return value;
  }
}

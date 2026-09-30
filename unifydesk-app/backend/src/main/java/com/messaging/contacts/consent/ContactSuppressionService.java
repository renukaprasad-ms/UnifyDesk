package com.messaging.contacts.consent;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.contacts.consent.ConsentTypes.ActorType;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import com.messaging.contacts.consent.ConsentTypes.SuppressionCommand;
import com.messaging.contacts.consent.ConsentTypes.SuppressionReason;
import com.messaging.contacts.consent.ConsentTypes.SuppressionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactSuppressionService {

  private static final Set<SuppressionReason> NON_LIFTABLE =
      Set.of(
          SuppressionReason.COMPLAINT,
          SuppressionReason.LEGAL_POLICY,
          SuppressionReason.ABUSE_POLICY);

  private static final Set<SuppressionReason> USER_CREATABLE =
      Set.of(
          SuppressionReason.MANUAL,
          SuppressionReason.DND,
          SuppressionReason.INVALID_DESTINATION,
          SuppressionReason.ABUSE_POLICY,
          SuppressionReason.LEGAL_POLICY);

  private final ContactSuppressionRepository repository;
  private final ContactAudienceProperties properties;
  private final ContactConsentTargetValidator targetValidator;

  public List<SuppressionSnapshot> list(long companyId, long contactId) {
    return repository.list(companyId, contactId);
  }

  public boolean isLiftable(SuppressionSnapshot suppression) {
    return suppression.state() == ConsentTypes.SuppressionState.ACTIVE
        && !NON_LIFTABLE.contains(suppression.reasonCode());
  }

  @Transactional
  public SuppressionSnapshot addManual(
      long companyId,
      long contactId,
      Long identityId,
      Channel channel,
      SuppressionReason reason,
      String sourceRef,
      String explanation,
      Instant occurredAt,
      String idempotencyKey,
      long userId) {
    if (channel == null || reason == null || !USER_CREATABLE.contains(reason)) {
      throw new BadRequestException("CONTACT_FIELD_INVALID");
    }
    return applyTrusted(
        new SuppressionCommand(
            companyId,
            contactId,
            identityId,
            channel,
            reason,
            PreferenceSource.USER_UI,
            sourceRef,
            ActorType.USER,
            Long.toString(userId),
            explanation,
            occurredAt == null ? Instant.now() : occurredAt,
            blank(idempotencyKey) ? "user:" + UUID.randomUUID() : idempotencyKey));
  }

  @Transactional
  public SuppressionSnapshot applyTrusted(SuppressionCommand command) {
    if (command == null
        || command.channel() == null
        || command.reasonCode() == null
        || command.source() == null
        || command.actorType() == null) {
      throw new BadRequestException("CONTACT_FIELD_INVALID");
    }
    validate(command.occurredAt(), command.idempotencyKey());
    targetValidator.requireWritableTarget(
        command.companyId(),
        command.contactId(),
        command.identityId(),
        command.channel());
    return repository.add(command);
  }

  @Transactional
  public SuppressionSnapshot liftFromUser(
      long companyId,
      long suppressionId,
      String reason,
      String idempotencyKey,
      long userId) {
    SuppressionSnapshot existing = repository.require(companyId, suppressionId);
    if (NON_LIFTABLE.contains(existing.reasonCode())) {
      throw new ForbiddenException("Suppression cannot be lifted by user policy");
    }
    return repository.lift(
        companyId,
        suppressionId,
        PreferenceSource.USER_UI,
        null,
        ActorType.USER,
        Long.toString(userId),
        reason,
        Instant.now(),
        blank(idempotencyKey) ? "user:" + UUID.randomUUID() : idempotencyKey);
  }

  private void validate(Instant occurredAt, String idempotencyKey) {
    if (occurredAt == null || blank(idempotencyKey) || idempotencyKey.length() > 160) {
      throw new BadRequestException("CONTACT_FIELD_INVALID");
    }
    Instant now = Instant.now();
    if (occurredAt.isAfter(now.plus(properties.getEventFutureSkew()))
        || occurredAt.isBefore(now.minus(properties.getEventMaxAge()))) {
      throw new BadRequestException("CONTACT_FIELD_INVALID");
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}

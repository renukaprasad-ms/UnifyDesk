package com.messaging.contacts.consent;

import com.messaging.common.exception.BadRequestException;
import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.contacts.consent.ConsentTypes.ActorType;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.PreferenceCommand;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSnapshot;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import com.messaging.contacts.consent.ConsentTypes.Purpose;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactPreferenceService {

  private final ContactPreferenceRepository repository;
  private final ContactAudienceProperties properties;

  public List<PreferenceSnapshot> list(long companyId, long contactId) {
    return repository.list(companyId, contactId);
  }

  @Transactional
  public PreferenceSnapshot setFromUser(
      long companyId,
      long contactId,
      Long identityId,
      Channel channel,
      Purpose purpose,
      PreferenceState state,
      PreferenceSource source,
      String sourceRef,
      String reason,
      Instant occurredAt,
      String idempotencyKey,
      long userId) {
    PreferenceSource effectiveSource =
        source == null ? PreferenceSource.USER_UI : source;
    return apply(
        new PreferenceCommand(
            companyId,
            contactId,
            identityId,
            channel,
            purpose,
            state,
            effectiveSource,
            sourceRef,
            ActorType.USER,
            Long.toString(userId),
            reason,
            occurredAt == null ? Instant.now() : occurredAt,
            idempotencyKey == null || idempotencyKey.isBlank()
                ? "user:" + UUID.randomUUID()
                : idempotencyKey));
  }

  @Transactional
  public PreferenceSnapshot applyTrusted(PreferenceCommand command) {
    if (command.actorType() == ActorType.USER && command.actorId() == null) {
      throw new BadRequestException("CONTACT_PERMISSION_DENIED");
    }
    return apply(command);
  }

  private PreferenceSnapshot apply(PreferenceCommand command) {
    if (command.channel() == null
        || command.purpose() == null
        || command.state() == null
        || command.source() == null
        || command.actorType() == null
        || command.idempotencyKey() == null
        || command.idempotencyKey().isBlank()
        || command.idempotencyKey().length() > 160) {
      throw new BadRequestException("CONTACT_FIELD_INVALID");
    }
    validateOccurredAt(command.occurredAt());
    return repository.apply(command);
  }

  private void validateOccurredAt(Instant occurredAt) {
    if (occurredAt == null) throw new BadRequestException("CONTACT_FIELD_INVALID");
    Instant now = Instant.now();
    if (occurredAt.isAfter(now.plus(properties.getEventFutureSkew()))
        || occurredAt.isBefore(now.minus(properties.getEventMaxAge()))) {
      throw new BadRequestException("CONTACT_FIELD_INVALID");
    }
  }
}

package com.messaging.contacts.integration;

import com.messaging.contacts.consent.ConsentTypes.ActorType;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import com.messaging.contacts.consent.ConsentTypes.SuppressionCommand;
import com.messaging.contacts.consent.ConsentTypes.SuppressionReason;
import com.messaging.contacts.consent.ContactSuppressionService;
import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import com.messaging.contacts.core.service.ContactIdentityLookup;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrustedProviderSuppressionBridge {

  private final BroadcastRecipientContactProvenanceRepository provenanceRepository;
  private final ContactIdentityLookup identityLookup;
  private final ContactSuppressionService suppressionService;

  public boolean suppressRecipient(
      long companyId,
      long recipientId,
      IdentityType identityType,
      Channel channel,
      SuppressionReason reason,
      PreferenceSource source,
      String sourceRef,
      Instant occurredAt,
      String idempotencyKey) {
    var provenance = provenanceRepository.find(companyId, recipientId).orElse(null);
    if (provenance == null
        || provenance.contactId() == null
        || provenance.contactIdentityId() == null
        || provenance.normalizedDestination() == null
        || provenance.normalizedDestination().isBlank()) {
      return false;
    }

    var resolved =
        identityLookup
            .findExact(companyId, identityType, null, provenance.normalizedDestination())
            .orElse(null);
    if (resolved == null
        || !Objects.equals(resolved.contactId(), provenance.contactId())
        || !Objects.equals(resolved.identityId(), provenance.contactIdentityId())) {
      return false;
    }

    suppressionService.applyTrusted(
        new SuppressionCommand(
            companyId,
            provenance.contactId(),
            provenance.contactIdentityId(),
            channel,
            reason,
            source,
            sourceRef,
            ActorType.PROVIDER,
            source.name(),
            "Trusted provider suppression feedback",
            occurredAt == null ? Instant.now() : occurredAt,
            idempotencyKey));
    return true;
  }
}

package com.messaging.contacts.consent;

import com.messaging.contacts.consent.ConsentTypes.*;
import com.messaging.contacts.consent.SuppressionFingerprintService.Fingerprint;
import com.messaging.contacts.integration.ContactCoreReadPort;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactEligibilityService {

  private final ContactCoreReadPort coreReadPort;
  private final ContactPreferenceRepository preferenceRepository;
  private final ContactSuppressionRepository suppressionRepository;
  private final SuppressionFingerprintService fingerprintService;
  private final EligibilityPolicy policy;

  public EligibilityResult evaluate(
      long companyId,
      long contactId,
      Long identityId,
      Channel channel,
      Purpose purpose,
      EligibilityContext context) {
    Optional<ContactChannelIdentity> identity =
        coreReadPort.loadIdentity(companyId, contactId, identityId, channel);
    if (identity.isEmpty()) {
      return ineligible(EligibilityReason.IDENTITY_MISSING, null, null);
    }
    ContactChannelIdentity selected = identity.get();
    PreferenceState preference =
        preferenceRepository.effectiveState(
            companyId, contactId, selected.identityId(), channel, purpose);
    Fingerprint fingerprint = safeFingerprint(channel, selected.normalizedDestination());
    if (suppressionRepository.isSuppressed(
        companyId, contactId, selected.identityId(), channel, fingerprint)) {
      return ineligible(
          EligibilityReason.SUPPRESSED, selected.identityId(), selected.normalizedDestination());
    }
    EligibilityReason preferenceBlock = preferenceBlock(channel, purpose, context, preference);
    if (preferenceBlock != null) {
      return ineligible(
          preferenceBlock, selected.identityId(), selected.normalizedDestination());
    }
    Optional<EligibilityReason> policyBlock =
        policy.additionalBlock(companyId, selected, channel, purpose, context);
    if (policyBlock.isPresent()) {
      return ineligible(
          policyBlock.get(), selected.identityId(), selected.normalizedDestination());
    }
    return new EligibilityResult(
        EligibilityDecision.ELIGIBLE,
        EligibilityReason.ELIGIBLE,
        selected.identityId(),
        selected.normalizedDestination(),
        policy.version(),
        Instant.now());
  }

  public Map<Long, EligibilityResult> evaluateBatch(
      long companyId,
      Collection<Long> contactIds,
      Channel channel,
      Purpose purpose,
      EligibilityContext context) {
    if (contactIds == null || contactIds.isEmpty()) return Map.of();
    Map<Long, ContactChannelIdentity> identities =
        coreReadPort.loadDeterministicIdentities(companyId, contactIds, channel);
    Map<Long, Long> identityIds = new HashMap<>();
    Map<Long, Fingerprint> fingerprints = new HashMap<>();
    for (var entry : identities.entrySet()) {
      identityIds.put(entry.getKey(), entry.getValue().identityId());
      Fingerprint fingerprint = safeFingerprint(channel, entry.getValue().normalizedDestination());
      if (fingerprint != null) fingerprints.put(entry.getKey(), fingerprint);
    }
    Map<Long, PreferenceState> preferences =
        preferenceRepository.effectiveStates(companyId, identityIds, channel, purpose);
    Set<Long> suppressed =
        suppressionRepository.suppressedContacts(companyId, identityIds, fingerprints, channel);

    Map<Long, EligibilityResult> result = new LinkedHashMap<>();
    for (Long contactId : contactIds) {
      ContactChannelIdentity identity = identities.get(contactId);
      if (identity == null) {
        result.put(contactId, ineligible(EligibilityReason.IDENTITY_MISSING, null, null));
        continue;
      }
      if (suppressed.contains(contactId)) {
        result.put(
            contactId,
            ineligible(
                EligibilityReason.SUPPRESSED,
                identity.identityId(),
                identity.normalizedDestination()));
        continue;
      }
      PreferenceState preference = preferences.getOrDefault(contactId, PreferenceState.UNKNOWN);
      EligibilityReason preferenceBlock = preferenceBlock(channel, purpose, context, preference);
      if (preferenceBlock != null) {
        result.put(
            contactId,
            ineligible(
                preferenceBlock, identity.identityId(), identity.normalizedDestination()));
        continue;
      }
      Optional<EligibilityReason> policyBlock =
          policy.additionalBlock(companyId, identity, channel, purpose, context);
      if (policyBlock.isPresent()) {
        result.put(
            contactId,
            ineligible(
                policyBlock.get(), identity.identityId(), identity.normalizedDestination()));
      } else {
        result.put(
            contactId,
            new EligibilityResult(
                EligibilityDecision.ELIGIBLE,
                EligibilityReason.ELIGIBLE,
                identity.identityId(),
                identity.normalizedDestination(),
                policy.version(),
                Instant.now()));
      }
    }
    return result;
  }

  private EligibilityReason preferenceBlock(
      Channel channel,
      Purpose purpose,
      EligibilityContext context,
      PreferenceState preference) {
    if (preference == PreferenceState.OPTED_OUT) return EligibilityReason.OPTED_OUT;
    if (preference != PreferenceState.OPTED_IN
        && policy.requiresAffirmativeOptIn(channel, purpose, context)) {
      return EligibilityReason.CONSENT_REQUIRED;
    }
    return null;
  }

  private Fingerprint safeFingerprint(Channel channel, String destination) {
    try {
      return fingerprintService.fingerprint(channel, destination);
    } catch (IllegalStateException notConfigured) {
      return null;
    }
  }

  private EligibilityResult ineligible(
      EligibilityReason reason, Long identityId, String normalizedDestination) {
    return new EligibilityResult(
        EligibilityDecision.INELIGIBLE,
        reason,
        identityId,
        normalizedDestination,
        policy.version(),
        Instant.now());
  }
}

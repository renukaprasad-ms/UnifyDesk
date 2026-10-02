package com.messaging.contacts.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import com.messaging.contacts.consent.ConsentTypes.SuppressionCommand;
import com.messaging.contacts.consent.ConsentTypes.SuppressionReason;
import com.messaging.contacts.consent.ContactSuppressionService;
import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import com.messaging.contacts.core.service.ContactIdentityLookup;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TrustedProviderSuppressionBridgeTests {

  @Test
  void exactRecipientProvenanceCreatesCanonicalSuppression() {
    BroadcastRecipientContactProvenanceRepository provenance =
        mock(BroadcastRecipientContactProvenanceRepository.class);
    ContactIdentityLookup identities = mock(ContactIdentityLookup.class);
    ContactSuppressionService suppressions = mock(ContactSuppressionService.class);
    TrustedProviderSuppressionBridge bridge =
        new TrustedProviderSuppressionBridge(provenance, identities, suppressions);
    when(provenance.find(10L, 70L))
        .thenReturn(
            Optional.of(
                new BroadcastRecipientContactProvenanceRepository.Provenance(
                    20L, 30L, "person@example.com")));
    when(identities.findExact(10L, IdentityType.EMAIL, null, "person@example.com"))
        .thenReturn(
            Optional.of(
                new ContactIdentityLookup.ResolvedIdentity(
                    20L, 30L, "person@example.com")));

    boolean applied =
        bridge.suppressRecipient(
            10L,
            70L,
            IdentityType.EMAIL,
            Channel.EMAIL,
            SuppressionReason.COMPLAINT,
            PreferenceSource.MANAGED_EMAIL_EVENT,
            "sns:event-1",
            Instant.parse("2026-10-02T03:00:00Z"),
            "managed-email:event-1");

    assertThat(applied).isTrue();
    ArgumentCaptor<SuppressionCommand> command = ArgumentCaptor.forClass(SuppressionCommand.class);
    verify(suppressions).applyTrusted(command.capture());
    assertThat(command.getValue().companyId()).isEqualTo(10L);
    assertThat(command.getValue().contactId()).isEqualTo(20L);
    assertThat(command.getValue().identityId()).isEqualTo(30L);
    assertThat(command.getValue().reasonCode()).isEqualTo(SuppressionReason.COMPLAINT);
    assertThat(command.getValue().idempotencyKey()).isEqualTo("managed-email:event-1");
  }

  @Test
  void provenanceMismatchCannotSuppressAnotherContact() {
    BroadcastRecipientContactProvenanceRepository provenance =
        mock(BroadcastRecipientContactProvenanceRepository.class);
    ContactIdentityLookup identities = mock(ContactIdentityLookup.class);
    ContactSuppressionService suppressions = mock(ContactSuppressionService.class);
    TrustedProviderSuppressionBridge bridge =
        new TrustedProviderSuppressionBridge(provenance, identities, suppressions);
    when(provenance.find(10L, 70L))
        .thenReturn(
            Optional.of(
                new BroadcastRecipientContactProvenanceRepository.Provenance(
                    20L, 30L, "person@example.com")));
    when(identities.findExact(10L, IdentityType.EMAIL, null, "person@example.com"))
        .thenReturn(
            Optional.of(
                new ContactIdentityLookup.ResolvedIdentity(
                    21L, 31L, "person@example.com")));

    boolean applied =
        bridge.suppressRecipient(
            10L,
            70L,
            IdentityType.EMAIL,
            Channel.EMAIL,
            SuppressionReason.HARD_BOUNCE,
            PreferenceSource.MANAGED_EMAIL_EVENT,
            "sns:event-2",
            Instant.parse("2026-10-02T03:00:00Z"),
            "managed-email:event-2");

    assertThat(applied).isFalse();
    verifyNoInteractions(suppressions);
  }

  @Test
  void legacyRecipientWithoutContactProvenanceDoesNotInventContact() {
    BroadcastRecipientContactProvenanceRepository provenance =
        mock(BroadcastRecipientContactProvenanceRepository.class);
    ContactIdentityLookup identities = mock(ContactIdentityLookup.class);
    ContactSuppressionService suppressions = mock(ContactSuppressionService.class);
    TrustedProviderSuppressionBridge bridge =
        new TrustedProviderSuppressionBridge(provenance, identities, suppressions);
    when(provenance.find(10L, 70L))
        .thenReturn(
            Optional.of(
                new BroadcastRecipientContactProvenanceRepository.Provenance(
                    null, null, "legacy@example.com")));

    boolean applied =
        bridge.suppressRecipient(
            10L,
            70L,
            IdentityType.EMAIL,
            Channel.EMAIL,
            SuppressionReason.HARD_BOUNCE,
            PreferenceSource.MANAGED_EMAIL_EVENT,
            "sns:event-3",
            Instant.parse("2026-10-02T03:00:00Z"),
            "managed-email:event-3");

    assertThat(applied).isFalse();
    verifyNoInteractions(identities, suppressions);
  }
}

package com.messaging.inbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import com.messaging.contacts.core.service.ContactIdentityLookup;
import com.messaging.contacts.core.service.IdentityNormalizer;
import com.messaging.inbox.enums.InboxParticipantType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class InboxParticipantContactLinkServiceTests {

  @Test
  void emailParticipantResolvesWithinCompanyWithoutCreatingContact() {
    NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(named.getJdbcTemplate()).thenReturn(jdbc);
    ContactIdentityLookup identities = mock(ContactIdentityLookup.class);
    when(identities.findExact(10L, IdentityType.EMAIL, null, "person@example.com"))
        .thenReturn(
            Optional.of(
                new ContactIdentityLookup.ResolvedIdentity(
                    20L, 30L, "person@example.com")));
    InboxParticipantContactLinkService service =
        new InboxParticipantContactLinkService(named, new IdentityNormalizer(), identities);

    var link =
        service.resolve(
            10L, 40L, InboxParticipantType.EMAIL_ADDRESS, "Person@Example.com");

    assertThat(link.resolution())
        .isEqualTo(InboxParticipantContactLinkService.Resolution.MATCHED);
    assertThat(link.contactId()).isEqualTo(20L);
    assertThat(link.contactIdentityId()).isEqualTo(30L);
    verify(identities).findExact(10L, IdentityType.EMAIL, null, "person@example.com");
    verify(jdbc).update(anyString(), any(Object[].class));
  }

  @Test
  void whatsappDigitsNormalizeToE164AndUnknownSenderStaysUnmatched() {
    NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(named.getJdbcTemplate()).thenReturn(jdbc);
    ContactIdentityLookup identities = mock(ContactIdentityLookup.class);
    when(identities.findExact(10L, IdentityType.PHONE, null, "+919876543210"))
        .thenReturn(Optional.empty());
    InboxParticipantContactLinkService service =
        new InboxParticipantContactLinkService(named, new IdentityNormalizer(), identities);

    var link =
        service.resolve(
            10L, 40L, InboxParticipantType.PHONE_NUMBER, "919876543210");

    assertThat(link.normalizedIdentity()).isEqualTo("+919876543210");
    assertThat(link.resolution())
        .isEqualTo(InboxParticipantContactLinkService.Resolution.UNMATCHED);
    assertThat(link.contactId()).isNull();
    verify(jdbc).update(anyString(), any(Object[].class));
  }
}

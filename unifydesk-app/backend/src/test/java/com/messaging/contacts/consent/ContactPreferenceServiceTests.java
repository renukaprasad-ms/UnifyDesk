package com.messaging.contacts.consent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.messaging.common.exception.BadRequestException;
import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.contacts.consent.ConsentTypes.ActorType;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.PreferenceCommand;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import com.messaging.contacts.consent.ConsentTypes.Purpose;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContactPreferenceServiceTests {

  private ContactPreferenceRepository repository;
  private ContactConsentTargetValidator targetValidator;
  private ContactPreferenceService service;

  @BeforeEach
  void setUp() {
    repository = mock(ContactPreferenceRepository.class);
    targetValidator = mock(ContactConsentTargetValidator.class);
    service =
        new ContactPreferenceService(
            repository, new ContactAudienceProperties(), targetValidator);
  }

  @Test
  void rejectsEveryNonUserUiSourceOnPublicMutation() {
    for (PreferenceSource source : PreferenceSource.values()) {
      if (source == PreferenceSource.USER_UI) continue;
      assertThrows(
          BadRequestException.class,
          () ->
              service.setFromUser(
                  1L,
                  2L,
                  null,
                  Channel.EMAIL,
                  Purpose.MARKETING,
                  PreferenceState.OPTED_OUT,
                  source,
                  null,
                  null,
                  Instant.now(),
                  "k-" + source.name(),
                  7L));
    }

    verifyNoInteractions(repository, targetValidator);
  }

  @Test
  void publicMutationOwnsActorAndSourceProvenance() {
    when(repository.apply(any())).thenReturn(null);

    service.setFromUser(
        1L,
        2L,
        null,
        Channel.EMAIL,
        Purpose.MARKETING,
        PreferenceState.OPTED_OUT,
        null,
        "ui",
        "user choice",
        Instant.now(),
        "user-key",
        7L);

    ArgumentCaptor<PreferenceCommand> command = ArgumentCaptor.forClass(PreferenceCommand.class);
    verify(repository).apply(command.capture());
    assertEquals(PreferenceSource.USER_UI, command.getValue().source());
    assertEquals(ActorType.USER, command.getValue().actorType());
    assertEquals("7", command.getValue().actorId());
    verify(targetValidator).requireWritableTarget(1L, 2L, null, Channel.EMAIL);
  }

  @Test
  void contactActionOwnsRecipientOptOutProvenance() {
    when(repository.apply(any())).thenReturn(null);

    service.setFromContactAction(
        1L,
        2L,
        3L,
        "email-unsubscribe:v1:action",
        "Recipient unsubscribed",
        Instant.now(),
        "contact-action:action");

    ArgumentCaptor<PreferenceCommand> command = ArgumentCaptor.forClass(PreferenceCommand.class);
    verify(repository).apply(command.capture());
    assertEquals(Channel.EMAIL, command.getValue().channel());
    assertEquals(Purpose.MARKETING, command.getValue().purpose());
    assertEquals(PreferenceState.OPTED_OUT, command.getValue().state());
    assertEquals(PreferenceSource.CONTACT_ACTION, command.getValue().source());
    assertEquals(ActorType.CONTACT, command.getValue().actorType());
    assertEquals("2", command.getValue().actorId());
  }

  @Test
  void importMutationUsesTrustedImportProvenance() {
    when(repository.apply(any())).thenReturn(null);

    service.setFromImport(
        1L,
        2L,
        null,
        Channel.SMS,
        Purpose.MARKETING,
        PreferenceState.OPTED_IN,
        "evidence",
        "Imported consent evidence",
        Instant.now(),
        "import:5:row:1:SMS",
        5L);

    ArgumentCaptor<PreferenceCommand> command = ArgumentCaptor.forClass(PreferenceCommand.class);
    verify(repository).apply(command.capture());
    assertEquals(PreferenceSource.IMPORT, command.getValue().source());
    assertEquals(ActorType.IMPORT, command.getValue().actorType());
    assertEquals("5", command.getValue().actorId());
  }
}

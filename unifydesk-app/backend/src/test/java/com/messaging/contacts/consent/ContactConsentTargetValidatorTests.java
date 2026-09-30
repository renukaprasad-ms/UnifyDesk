package com.messaging.contacts.consent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.common.exception.NotFoundException;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.integration.ContactCoreReadPort;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContactConsentTargetValidatorTests {

  private ContactCoreReadPort coreReadPort;
  private ContactConsentTargetValidator validator;

  @BeforeEach
  void setUp() {
    coreReadPort = mock(ContactCoreReadPort.class);
    validator = new ContactConsentTargetValidator(coreReadPort);
  }

  @Test
  void rejectsIdentityOwnedByDifferentContact() {
    when(coreReadPort.loadIdentity(1L, 10L, 99L, Channel.EMAIL))
        .thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () -> validator.requireWritableTarget(1L, 10L, 99L, Channel.EMAIL));
  }

  @Test
  void rejectsIdentityOwnedByDifferentCompanyWithoutEnumeration() {
    when(coreReadPort.loadIdentity(2L, 10L, 99L, Channel.EMAIL))
        .thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () -> validator.requireWritableTarget(2L, 10L, 99L, Channel.EMAIL));
  }

  @Test
  void rejectsChannelIdentityMismatch() {
    when(coreReadPort.loadIdentity(1L, 10L, 99L, Channel.SMS))
        .thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () -> validator.requireWritableTarget(1L, 10L, 99L, Channel.SMS));
  }

  @Test
  void allowsContactLevelNullIdentityForActiveContact() {
    when(coreReadPort.activeContactExists(1L, 10L)).thenReturn(true);

    assertDoesNotThrow(
        () -> validator.requireWritableTarget(1L, 10L, null, Channel.WHATSAPP));

    verify(coreReadPort).activeContactExists(1L, 10L);
    verify(coreReadPort, never())
        .loadIdentity(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void rejectsNullIdentityWhenContactIsNotActiveInTenant() {
    when(coreReadPort.activeContactExists(1L, 10L)).thenReturn(false);

    assertThrows(
        NotFoundException.class,
        () -> validator.requireWritableTarget(1L, 10L, null, Channel.EMAIL));
  }
}

package com.messaging.contacts.consent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.messaging.contacts.consent.RecipientActionTokenService.ValidatedRecipientAction;
import org.junit.jupiter.api.Test;

class RecipientEmailUnsubscribeServiceTests {

  @Test
  void recipientActionWritesCanonicalEmailMarketingOptOut() {
    RecipientActionTokenService tokenService = mock(RecipientActionTokenService.class);
    ContactPreferenceService preferenceService = mock(ContactPreferenceService.class);
    when(tokenService.validate("opaque"))
        .thenReturn(new ValidatedRecipientAction(11L, 22L, 33L, "action-44"));
    RecipientEmailUnsubscribeService service =
        new RecipientEmailUnsubscribeService(tokenService, preferenceService);

    service.unsubscribe("opaque");

    verify(preferenceService)
        .setFromContactAction(
            eq(11L),
            eq(22L),
            eq(33L),
            eq("email-unsubscribe:v1:action-44"),
            eq("Recipient unsubscribed from marketing email"),
            any(),
            eq("contact-action:action-44"));
  }

  @Test
  void replayUsesTheSameDeterministicIdempotencyKey() {
    RecipientActionTokenService tokenService = mock(RecipientActionTokenService.class);
    ContactPreferenceService preferenceService = mock(ContactPreferenceService.class);
    when(tokenService.validate("opaque"))
        .thenReturn(new ValidatedRecipientAction(11L, 22L, 33L, "same-action"));
    RecipientEmailUnsubscribeService service =
        new RecipientEmailUnsubscribeService(tokenService, preferenceService);

    service.unsubscribe("opaque");
    service.unsubscribe("opaque");

    verify(preferenceService, times(2))
        .setFromContactAction(
            eq(11L),
            eq(22L),
            eq(33L),
            anyString(),
            anyString(),
            any(),
            eq("contact-action:same-action"));
  }

  @Test
  void invalidTokenNeverTouchesPreferenceState() {
    RecipientActionTokenService tokenService = mock(RecipientActionTokenService.class);
    ContactPreferenceService preferenceService = mock(ContactPreferenceService.class);
    when(tokenService.validate("bad")).thenThrow(new InvalidRecipientActionException());
    RecipientEmailUnsubscribeService service =
        new RecipientEmailUnsubscribeService(tokenService, preferenceService);

    assertThatThrownBy(() -> service.unsubscribe("bad"))
        .isInstanceOf(InvalidRecipientActionException.class);
    verifyNoInteractions(preferenceService);
  }
}

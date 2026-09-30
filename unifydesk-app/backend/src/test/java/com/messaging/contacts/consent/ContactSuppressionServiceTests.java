package com.messaging.contacts.consent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.SuppressionReason;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContactSuppressionServiceTests {

  @Test
  void manualSuppressionValidatesContactIdentityChannelTarget() {
    ContactSuppressionRepository repository = mock(ContactSuppressionRepository.class);
    ContactConsentTargetValidator targetValidator = mock(ContactConsentTargetValidator.class);
    ContactSuppressionService service =
        new ContactSuppressionService(
            repository, new ContactAudienceProperties(), targetValidator);
    when(repository.add(any())).thenReturn(null);

    service.addManual(
        1L,
        2L,
        9L,
        Channel.EMAIL,
        SuppressionReason.MANUAL,
        null,
        "manual",
        Instant.now(),
        "suppress-1",
        7L);

    verify(targetValidator).requireWritableTarget(1L, 2L, 9L, Channel.EMAIL);
  }
}

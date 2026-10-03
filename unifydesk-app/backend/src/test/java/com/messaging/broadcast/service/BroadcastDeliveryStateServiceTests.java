package com.messaging.broadcast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.messaging.broadcast.entity.BroadcastDeliveryAttempt;
import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.enums.BroadcastAttemptStatus;
import com.messaging.broadcast.enums.BroadcastRecipientStatus;
import com.messaging.broadcast.repository.BroadcastDeliveryAttemptRepository;
import com.messaging.broadcast.repository.BroadcastRecipientRepository;
import com.messaging.broadcast.transport.BroadcastEmailResult;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BroadcastDeliveryStateServiceTests {

  @Test
  void skippedRecipientTransitionsStatsWithoutProviderAcceptance() {
    BroadcastRecipientRepository recipientRepository = mock(BroadcastRecipientRepository.class);
    BroadcastDeliveryAttemptRepository attemptRepository =
        mock(BroadcastDeliveryAttemptRepository.class);
    BroadcastStatsService statsService = mock(BroadcastStatsService.class);
    BroadcastDeliveryStateService service =
        new BroadcastDeliveryStateService(
            recipientRepository, attemptRepository, statsService);

    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setId(10L);
    recipient.setRunId(20L);
    recipient.setStatus(BroadcastRecipientStatus.PROCESSING);
    when(recipientRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(recipient));

    service.skipProcessing(10L, "CONTACT_OPTED_OUT", "Contact opted out");

    assertThat(recipient.getStatus()).isEqualTo(BroadcastRecipientStatus.SKIPPED);
    assertThat(recipient.getProviderMessageId()).isNull();
    assertThat(recipient.getAcceptedAt()).isNull();
    verify(statsService)
        .transition(
            20L, BroadcastRecipientStatus.PROCESSING, BroadcastRecipientStatus.SKIPPED);
    verifyNoInteractions(attemptRepository);
  }

  @Test
  void acceptedResultDoesNotErasePreGeneratedInternetMessageId() {
    BroadcastRecipientRepository recipientRepository = mock(BroadcastRecipientRepository.class);
    BroadcastDeliveryAttemptRepository attemptRepository =
        mock(BroadcastDeliveryAttemptRepository.class);
    BroadcastStatsService statsService = mock(BroadcastStatsService.class);
    BroadcastDeliveryStateService service =
        new BroadcastDeliveryStateService(
            recipientRepository, attemptRepository, statsService);

    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setId(10L);
    recipient.setRunId(20L);
    recipient.setStatus(BroadcastRecipientStatus.PROCESSING);
    recipient.setInternetMessageId("<broadcast.correlation@example.test>");
    when(recipientRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(recipient));

    BroadcastDeliveryAttempt attempt = new BroadcastDeliveryAttempt();
    attempt.setId(30L);
    attempt.setRecipientId(10L);
    attempt.setRunId(20L);
    attempt.setAttemptNo(1);
    attempt.setProviderType(BroadcastSenderProvider.AMAZON_SES);
    attempt.setStatus(BroadcastAttemptStatus.PROCESSING);
    when(attemptRepository.findById(30L)).thenReturn(Optional.of(attempt));

    Instant acceptedAt = Instant.parse("2026-09-21T10:15:30Z");
    service.accepted(
        new BroadcastDeliveryStateService.AttemptContext(10L, 20L, 30L, 1),
        new BroadcastEmailResult(null, null, acceptedAt));

    assertThat(recipient.getStatus()).isEqualTo(BroadcastRecipientStatus.ACCEPTED_BY_PROVIDER);
    assertThat(recipient.getInternetMessageId())
        .isEqualTo("<broadcast.correlation@example.test>");
    assertThat(recipient.getAcceptedAt()).isEqualTo(acceptedAt);
    assertThat(attempt.getStatus()).isEqualTo(BroadcastAttemptStatus.ACCEPTED);
    verify(statsService)
        .transition(
            20L,
            BroadcastRecipientStatus.PROCESSING,
            BroadcastRecipientStatus.ACCEPTED_BY_PROVIDER);
  }
}

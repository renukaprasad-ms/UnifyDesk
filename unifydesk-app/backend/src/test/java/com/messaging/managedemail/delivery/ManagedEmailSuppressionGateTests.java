package com.messaging.managedemail.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.entity.BroadcastSuppression;
import com.messaging.broadcast.enums.BroadcastRecipientStatus;
import com.messaging.broadcast.enums.BroadcastSuppressionReason;
import com.messaging.broadcast.repository.BroadcastRecipientRepository;
import com.messaging.broadcast.repository.BroadcastSuppressionRepository;
import com.messaging.broadcast.service.BroadcastStatsService;
import com.messaging.channel.enums.ChannelType;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedEmailSuppressionGateTests {

  @Test
  void everyActiveSuppressionReasonStopsManagedSend() {
    for (BroadcastSuppressionReason reason : BroadcastSuppressionReason.values()) {
      BroadcastSuppressionRepository suppressions = mock(BroadcastSuppressionRepository.class);
      BroadcastRecipientRepository recipients = mock(BroadcastRecipientRepository.class);
      BroadcastStatsService stats = mock(BroadcastStatsService.class);
      BroadcastSuppression suppression = new BroadcastSuppression();
      suppression.setCompanyId(10L);
      suppression.setChannelType(ChannelType.EMAIL);
      suppression.setNormalizedDestination("recipient@example.test");
      suppression.setReason(reason);
      suppression.setActive(true);
      BroadcastRecipient recipient = new BroadcastRecipient();
      recipient.setId(70L);
      recipient.setCompanyId(10L);
      recipient.setRunId(60L);
      recipient.setStatus(BroadcastRecipientStatus.PROCESSING);

      when(suppressions.findByCompanyIdAndChannelTypeAndNormalizedDestination(
              10L, ChannelType.EMAIL, "recipient@example.test"))
          .thenReturn(Optional.of(suppression));
      when(recipients.findByIdForUpdate(70L)).thenReturn(Optional.of(recipient));

      ManagedEmailSuppressionGate gate =
          new ManagedEmailSuppressionGate(suppressions, recipients, stats);

      assertThat(gate.skipIfSuppressed(10L, 70L, "recipient@example.test")).isTrue();
      assertThat(recipient.getStatus()).isEqualTo(BroadcastRecipientStatus.SKIPPED);
      verify(recipients).save(recipient);
      verify(stats)
          .transition(
              60L, BroadcastRecipientStatus.PROCESSING, BroadcastRecipientStatus.SKIPPED);
    }
  }

  @Test
  void sameDestinationSuppressionFromAnotherCompanyDoesNotMatch() {
    BroadcastSuppressionRepository suppressions = mock(BroadcastSuppressionRepository.class);
    BroadcastRecipientRepository recipients = mock(BroadcastRecipientRepository.class);
    BroadcastStatsService stats = mock(BroadcastStatsService.class);
    when(suppressions.findByCompanyIdAndChannelTypeAndNormalizedDestination(
            10L, ChannelType.EMAIL, "recipient@example.test"))
        .thenReturn(Optional.empty());

    ManagedEmailSuppressionGate gate =
        new ManagedEmailSuppressionGate(suppressions, recipients, stats);

    assertThat(gate.skipIfSuppressed(10L, 70L, "recipient@example.test")).isFalse();
    verifyNoInteractions(recipients, stats);
  }

  @Test
  void crossCompanyRecipientIsNeverMutated() {
    BroadcastSuppressionRepository suppressions = mock(BroadcastSuppressionRepository.class);
    BroadcastRecipientRepository recipients = mock(BroadcastRecipientRepository.class);
    BroadcastStatsService stats = mock(BroadcastStatsService.class);
    BroadcastSuppression suppression = new BroadcastSuppression();
    suppression.setActive(true);
    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setId(70L);
    recipient.setCompanyId(11L);
    recipient.setStatus(BroadcastRecipientStatus.PROCESSING);

    when(suppressions.findByCompanyIdAndChannelTypeAndNormalizedDestination(
            10L, ChannelType.EMAIL, "recipient@example.test"))
        .thenReturn(Optional.of(suppression));
    when(recipients.findByIdForUpdate(70L)).thenReturn(Optional.of(recipient));

    ManagedEmailSuppressionGate gate =
        new ManagedEmailSuppressionGate(suppressions, recipients, stats);

    assertThat(gate.skipIfSuppressed(10L, 70L, "recipient@example.test")).isTrue();
    verify(recipients, never()).save(any());
    verifyNoInteractions(stats);
  }
}

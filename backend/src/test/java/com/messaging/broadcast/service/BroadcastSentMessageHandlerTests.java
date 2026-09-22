package com.messaging.broadcast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.enums.BroadcastRecipientStatus;
import com.messaging.broadcast.repository.BroadcastMessageRepository;
import com.messaging.broadcast.repository.BroadcastRecipientRepository;
import com.messaging.broadcast.repository.BroadcastRunRepository;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BroadcastSentMessageHandlerTests {

  @Test
  void sentFolderCopyRecoversUnknownDeliveryWithoutEnteringInbox() {
    BroadcastRecipientRepository recipientRepository = mock(BroadcastRecipientRepository.class);
    BroadcastRunRepository runRepository = mock(BroadcastRunRepository.class);
    BroadcastMessageRepository messageRepository = mock(BroadcastMessageRepository.class);
    BroadcastStatsService statsService = mock(BroadcastStatsService.class);
    BroadcastProgressPublisher progressPublisher = mock(BroadcastProgressPublisher.class);

    BroadcastSentMessageHandler handler =
        new BroadcastSentMessageHandler(
            recipientRepository,
            runRepository,
            messageRepository,
            statsService,
            progressPublisher);

    BroadcastRecipient recipient = new BroadcastRecipient();
    recipient.setId(11L);
    recipient.setRunId(22L);
    recipient.setConnectionId(33L);
    recipient.setStatus(BroadcastRecipientStatus.UNKNOWN);
    recipient.setProviderCorrelationId("corr-1");
    when(recipientRepository.findByProviderCorrelationId("corr-1"))
        .thenReturn(Optional.of(recipient));
    when(runRepository.findById(22L)).thenReturn(Optional.empty());

    ChannelConnection connection = new ChannelConnection();
    connection.setId(33L);

    boolean consumed =
        handler.handle(
            connection,
            ProviderType.MICROSOFT,
            "corr-1",
            "provider-message",
            "<real-message-id@example.com>",
            Instant.parse("2026-09-21T11:00:00Z"));

    assertThat(consumed).isTrue();
    assertThat(recipient.getStatus()).isEqualTo(BroadcastRecipientStatus.ACCEPTED_BY_PROVIDER);
    assertThat(recipient.getProviderMessageId()).isEqualTo("provider-message");
    assertThat(recipient.getInternetMessageId()).isEqualTo("<real-message-id@example.com>");
    verify(statsService)
        .transition(
            22L,
            BroadcastRecipientStatus.UNKNOWN,
            BroadcastRecipientStatus.ACCEPTED_BY_PROVIDER);
    verify(progressPublisher).publish(22L, true);
  }
}

package com.messaging.broadcast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.messaging.broadcast.dto.BroadcastChannelReadinessResponse.ChannelReadiness;
import com.messaging.broadcast.dto.BroadcastChannelReadinessResponse.Configuration;
import com.messaging.broadcast.dto.BroadcastSenderResponse;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.enums.BroadcastSenderStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.meta.runtime.MetaWhatsAppControlPlaneProjectionRepository;
import com.messaging.channel.repository.ChannelConnectionRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BroadcastChannelReadinessServiceTests {

  @Test
  void whatsappCapabilitiesAreTruthfulForReadyMetaSender() {
    BroadcastSenderService senderService = mock(BroadcastSenderService.class);
    MetaWhatsAppControlPlaneProjectionRepository projections =
        mock(MetaWhatsAppControlPlaneProjectionRepository.class);
    ChannelConnectionRepository channelConnections = mock(ChannelConnectionRepository.class);
    BroadcastChannelReadinessService service =
        new BroadcastChannelReadinessService(senderService, projections, channelConnections);

    when(senderService.list(7L))
        .thenReturn(
            List.of(
                new BroadcastSenderResponse(
                    "55",
                    "56",
                    "57",
                    BroadcastSenderProvider.META_WHATSAPP,
                    BroadcastSenderManagementMode.CUSTOMER_MANAGED,
                    BroadcastSenderStatus.ACTIVE,
                    "+919800000000",
                    "Brand WhatsApp",
                    null,
                    "Meta WhatsApp",
                    "META",
                    true,
                    true,
                    null,
                    null,
                    null,
                    Instant.now())));

    ChannelReadiness whatsapp =
        service.readiness(7L).stream()
            .filter(value -> value.channel() == ChannelType.WHATSAPP)
            .findFirst()
            .orElseThrow();

    assertThat(whatsapp.configuration()).isEqualTo(Configuration.READY);
    assertThat(whatsapp.senders()).hasSize(1);
    assertThat(whatsapp.senders().getFirst().capabilities().providerAcceptance()).isTrue();
    assertThat(whatsapp.senders().getFirst().capabilities().deliveryEvents()).isTrue();
    assertThat(whatsapp.senders().getFirst().capabilities().readEvents()).isTrue();
    assertThat(whatsapp.senders().getFirst().capabilities().inboundReplies()).isTrue();
    assertThat(whatsapp.senders().getFirst().capabilities().templateRequired()).isTrue();
  }
}

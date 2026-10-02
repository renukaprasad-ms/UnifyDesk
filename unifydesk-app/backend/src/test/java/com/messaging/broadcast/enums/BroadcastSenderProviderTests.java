package com.messaging.broadcast.enums;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.channel.enums.ChannelType;
import org.junit.jupiter.api.Test;

class BroadcastSenderProviderTests {

  @Test
  void metaWhatsAppIsAWhatsAppBroadcastProvider() {
    assertThat(BroadcastSenderProvider.META_WHATSAPP.channelType())
        .isEqualTo(ChannelType.WHATSAPP);
    assertThat(BroadcastSenderProvider.META_WHATSAPP.supports(ChannelType.WHATSAPP)).isTrue();
    assertThat(BroadcastSenderProvider.META_WHATSAPP.supports(ChannelType.EMAIL)).isFalse();
  }
}

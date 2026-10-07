package com.messaging.channel.zoho;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.channel.email.EmailReadStateProvider;
import com.messaging.channel.email.EmailSendProvider;
import com.messaging.channel.email.EmailSyncProvider;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.provider.DisconnectableChannelProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ZohoMailProviderTests {

  @Test
  void exposesAllInboxEmailCapabilities() {
    ZohoMailProvider provider =
        new ZohoMailProvider(
            Mockito.mock(ZohoMailSendService.class),
            Mockito.mock(ZohoMailSyncQueueService.class),
            Mockito.mock(ZohoMailReadStateService.class),
            Mockito.mock(ZohoMailDisconnectService.class));

    assertThat(provider.providerType()).isEqualTo(ProviderType.ZOHO_MAIL);
    assertThat(provider).isInstanceOf(EmailSendProvider.class);
    assertThat(provider).isInstanceOf(EmailSyncProvider.class);
    assertThat(provider).isInstanceOf(EmailReadStateProvider.class);
    assertThat(provider).isInstanceOf(DisconnectableChannelProvider.class);
    assertThat(ProviderType.ZOHO_MAIL.capabilities()).containsExactlyInAnyOrder(
        com.messaging.channel.enums.ChannelConnectionCapability.INBOX);
  }
}

package com.messaging.managedsms.metareach;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.messaging.broadcast.transport.BroadcastTransportException;

class MetaReachApiContractTests {
  @Test void disabledWithoutExplicitContractConfirmation() {
    MetaReachProperties p=new MetaReachProperties();
    p.setEnabled(true);
    p.setApiKey("dummy");
    assertThat(p.ready()).isFalse();
    p.setContractConfirmed(true);
    assertThat(p.ready()).isTrue();
    p.setRouteId("not-a-route");
    assertThat(p.ready()).isFalse();
  }
  @Test void normalizesIndianMobilesOnly() {
    assertThat(MetaReachSmsClient.mobile("+91 98765 43210")).isEqualTo("919876543210");
    assertThat(MetaReachSmsClient.mobile("9876543210")).isEqualTo("919876543210");
    assertThatThrownBy(() -> MetaReachSmsClient.mobile("12345678"))
        .isInstanceOf(BroadcastTransportException.class);
    assertThatThrownBy(() -> MetaReachSmsClient.mobile("+1 212 555 0123"))
        .isInstanceOf(BroadcastTransportException.class);
  }
}

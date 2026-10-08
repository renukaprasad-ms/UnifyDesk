package com.messaging.managedsms.metareach;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  @Test void parsesPublishedSendAndDeliveryContracts() throws Exception {
    ObjectMapper json=new ObjectMapper();
    var accepted=json.readTree("""
      {"status":"Success","code":"011",
       "data":{"messageid":"MzQz","totnumber":"1","totalcredit":"1"}}
      """);
    assertThat(MetaReachSmsClient.acceptedMessageId(accepted)).isEqualTo("MzQz");
    var noCredits=json.readTree("""
      {"status":"false","code":"008","description":"Insufficient credit"}
      """);
    assertThatThrownBy(() -> MetaReachSmsClient.acceptedMessageId(noCredits))
        .isInstanceOf(BroadcastTransportException.class);
    assertThat(MetaReachDlrScheduler.deliveryEventType("delivered")).isEqualTo("DELIVERED");
    assertThat(MetaReachDlrScheduler.deliveryEventType("submitted")).isNull();
    assertThat(MetaReachDlrScheduler.deliveryEventType("failed")).isEqualTo("FAILED");
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

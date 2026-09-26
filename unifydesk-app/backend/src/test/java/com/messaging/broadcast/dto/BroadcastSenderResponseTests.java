package com.messaging.broadcast.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BroadcastSenderResponseTests {

  @Test
  void managedSenderHidesPlatformRegionQuotaAndValidationSnapshot() {
    BroadcastSender sender = sender();
    BroadcastSenderConnection connection = connection(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    connection.setRegion("us-east-1");
    connection.setProductionAccessEnabled(false);
    connection.setSendingEnabled(true);
    connection.setMax24HourSend(50000.0);
    connection.setMaxSendRate(14.0);
    connection.setSentLast24Hours(12345.0);
    connection.setLastValidatedAt(Instant.parse("2026-09-26T10:00:00Z"));

    BroadcastSenderResponse response = BroadcastSenderResponse.from(sender, connection);

    assertThat(response.region()).isEqualTo("MANAGED");
    assertThat(response.max24HourSend()).isNull();
    assertThat(response.maxSendRate()).isNull();
    assertThat(response.sentLast24Hours()).isNull();
    assertThat(response.lastValidatedAt()).isNull();
    assertThat(response.sendingEnabled()).isTrue();
    assertThat(response.productionAccessEnabled()).isTrue();
  }

  @Test
  void customerManagedSesKeepsCustomerOwnedProviderFields() {
    BroadcastSender sender = sender();
    BroadcastSenderConnection connection = connection(BroadcastSenderManagementMode.CUSTOMER_MANAGED);
    Instant validated = Instant.parse("2026-09-26T10:00:00Z");
    connection.setRegion("ap-south-1");
    connection.setProductionAccessEnabled(true);
    connection.setSendingEnabled(true);
    connection.setMax24HourSend(1000.0);
    connection.setMaxSendRate(5.0);
    connection.setSentLast24Hours(100.0);
    connection.setLastValidatedAt(validated);

    BroadcastSenderResponse response = BroadcastSenderResponse.from(sender, connection);

    assertThat(response.region()).isEqualTo("ap-south-1");
    assertThat(response.max24HourSend()).isEqualTo(1000.0);
    assertThat(response.maxSendRate()).isEqualTo(5.0);
    assertThat(response.sentLast24Hours()).isEqualTo(100.0);
    assertThat(response.lastValidatedAt()).isEqualTo(validated);
    assertThat(response.productionAccessEnabled()).isTrue();
  }

  private BroadcastSender sender() {
    BroadcastSender sender = new BroadcastSender();
    sender.setId(20L);
    sender.setProvider(BroadcastSenderProvider.AMAZON_SES);
    sender.setStatus(BroadcastSenderStatus.ACTIVE);
    sender.setFromAddress("offers@example.test");
    return sender;
  }

  private BroadcastSenderConnection connection(BroadcastSenderManagementMode mode) {
    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(30L);
    connection.setChannelConnectionId(30L);
    connection.setManagementMode(mode);
    connection.setProvider(BroadcastSenderProvider.AMAZON_SES);
    connection.setName(
        mode == BroadcastSenderManagementMode.MESSAGING_MANAGED
            ? "UnifyDesk Managed Email"
            : "Customer SES");
    return connection;
  }
}

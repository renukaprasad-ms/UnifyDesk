package com.messaging.managedsms.delivery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.billing.service.ManagedFinancialExposureGuard;
import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.common.exception.BadRequestException;
import com.messaging.managedsms.metering.ManagedSmsSegmentCalculator;
import com.messaging.managedsms.metering.ManagedSmsUsageService;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import com.messaging.managedsms.runtime.ManagedSmsTemplateService;
import com.messaging.managedsms.safety.ManagedSmsProviderSafetyService;
import org.junit.jupiter.api.Test;

class ManagedSmsLaunchGuardRound2Tests {

  @Test
  void absoluteCampaignCapBlocksBeforeRuntimeOrProviderWork() {
    ManagedSmsRuntimeRepository runtime = mock(ManagedSmsRuntimeRepository.class);
    ManagedSmsProviderSafetyService safety = mock(ManagedSmsProviderSafetyService.class);
    ManagedSmsRuntimeProperties properties = new ManagedSmsRuntimeProperties();
    properties.setEnabled(true);
    properties.setPlatformSendingEnabled(true);
    properties.setProvider("MSG91_SMS");
    properties.setCampaignMaxRecipients(100);

    ManagedSmsLaunchGuard guard =
        new ManagedSmsLaunchGuard(
            runtime,
            properties,
            mock(ManagedSmsTemplateService.class),
            safety,
            mock(ManagedSmsUsageService.class),
            mock(ManagedFinancialExposureGuard.class),
            mock(ManagedSmsSegmentCalculator.class),
            new ObjectMapper());

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setManagementMode(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    connection.setProvider(BroadcastSenderProvider.MSG91_SMS);

    BroadcastSender sender = new BroadcastSender();
    sender.setProvider(BroadcastSenderProvider.MSG91_SMS);

    assertThatThrownBy(
            () ->
                guard.validateLaunch(
                    10L, new BroadcastMessage(), sender, connection, 101L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("MANAGED_SMS_LIMIT_REACHED");

    verifyNoInteractions(runtime, safety);
  }
}

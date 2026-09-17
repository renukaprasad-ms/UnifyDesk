package com.messaging.channel.service;

import com.messaging.billing.entitlement.EntitlementKeys;
import com.messaging.billing.service.EntitlementService;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.SenderIdentityType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelEntitlementService {

  private final EntitlementService entitlementService;
  private final ChannelConnectionRepository connectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;

  @Transactional(readOnly = true)
  public void assertCanCreateConnection(Long companyId, ChannelType channelType) {
    String key =
        switch (channelType) {
          case EMAIL -> EntitlementKeys.EMAIL_ACCOUNTS;
          case WHATSAPP -> EntitlementKeys.WHATSAPP_ACCOUNTS;
          case SMS -> EntitlementKeys.SMS_ACCOUNTS;
        };
    long current =
        connectionRepository.countByCompanyIdAndChannelTypeAndStatusNot(
            companyId, channelType, ChannelConnectionStatus.DISCONNECTED);
    entitlementService.assertWithinLimit(companyId, key, current);
  }

  @Transactional(readOnly = true)
  public void assertCanCreateSender(Long companyId, SenderIdentityType identityType) {
    String key =
        switch (identityType) {
          case PHONE_NUMBER -> EntitlementKeys.WHATSAPP_NUMBERS;
          case SMS_HEADER -> EntitlementKeys.SMS_SENDER_IDS;
          case EMAIL_ADDRESS -> null;
        };
    if (key == null) return;

    long current =
        senderIdentityRepository.countByConnectionCompanyIdAndIdentityType(companyId, identityType);
    entitlementService.assertWithinLimit(companyId, key, current);
  }
}

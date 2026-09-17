package com.messaging.channel.service;

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

  public static final String EMAIL_ACCOUNTS = "EMAIL_ACCOUNTS";
  public static final String WHATSAPP_ACCOUNTS = "WHATSAPP_ACCOUNTS";
  public static final String WHATSAPP_NUMBERS = "WHATSAPP_NUMBERS";
  public static final String SMS_ACCOUNTS = "SMS_ACCOUNTS";
  public static final String SMS_SENDER_IDS = "SMS_SENDER_IDS";

  private final EntitlementService entitlementService;
  private final ChannelConnectionRepository connectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;

  @Transactional(readOnly = true)
  public void assertCanCreateConnection(Long companyId, ChannelType channelType) {
    String key =
        switch (channelType) {
          case EMAIL -> EMAIL_ACCOUNTS;
          case WHATSAPP -> WHATSAPP_ACCOUNTS;
          case SMS -> SMS_ACCOUNTS;
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
          case PHONE_NUMBER -> WHATSAPP_NUMBERS;
          case SMS_HEADER -> SMS_SENDER_IDS;
          case EMAIL_ADDRESS -> null;
        };
    if (key == null) return;

    long current =
        senderIdentityRepository.countByConnectionCompanyIdAndIdentityType(companyId, identityType);
    entitlementService.assertWithinLimit(companyId, key, current);
  }
}

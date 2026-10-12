package com.messaging.channel.zoho;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ZohoMailConnectionLookup {

  private final ChannelConnectionRepository connectionRepository;

  public ChannelConnection requireConnection(Long companyId, Long connectionId) {
    ChannelConnection connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("Zoho Mail connection not found"));
    if (connection.getProviderType() != ProviderType.ZOHO_MAIL) {
      throw new BadRequestException("Channel connection is not a Zoho Mail mailbox");
    }
    return connection;
  }

  public ChannelConnection requireActive(Long companyId, Long connectionId) {
    ChannelConnection connection = requireConnection(companyId, connectionId);
    if (connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
      throw new BadRequestException("Zoho Mail connection is not active");
    }
    return connection;
  }
}

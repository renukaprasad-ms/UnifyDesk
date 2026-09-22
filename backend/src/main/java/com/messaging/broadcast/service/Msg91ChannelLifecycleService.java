package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderConnectionStatus;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.enums.BroadcastSenderStatus;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.broadcast.repository.BroadcastSenderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class Msg91ChannelLifecycleService {

  private final BroadcastSenderConnectionRepository connectionRepository;
  private final BroadcastSenderRepository senderRepository;

  @Transactional
  public void disconnect(Long companyId, Long channelConnectionId) {
    BroadcastSenderConnection connection =
        connectionRepository
            .findByChannelConnectionIdAndCompanyId(channelConnectionId, companyId)
            .orElse(null);
    if (connection == null || connection.getProvider() != BroadcastSenderProvider.MSG91) return;

    connection.setStatus(BroadcastSenderConnectionStatus.DISABLED);
    connection.setSendingEnabled(false);
    connection.setEncryptedCredentials(null);
    connectionRepository.save(connection);

    List<BroadcastSender> senders = senderRepository.findByConnectionId(connection.getId());
    senders.forEach(sender -> sender.setStatus(BroadcastSenderStatus.DISABLED));
    senderRepository.saveAll(senders);
  }
}

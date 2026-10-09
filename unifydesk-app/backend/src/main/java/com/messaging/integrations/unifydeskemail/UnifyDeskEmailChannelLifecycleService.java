package com.messaging.integrations.unifydeskemail;

import com.messaging.broadcast.enums.BroadcastSenderConnectionStatus;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.enums.BroadcastSenderStatus;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.broadcast.repository.BroadcastSenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnifyDeskEmailChannelLifecycleService {
  private final BroadcastSenderConnectionRepository connections;
  private final BroadcastSenderRepository senders;

  @Transactional
  public void disconnect(Long companyId, Long connectionId) {
    var connection = connections.findByChannelConnectionIdAndCompanyId(connectionId, companyId)
        .orElse(null);
    if (connection == null || connection.getProvider() != BroadcastSenderProvider.UNIFYDESK_EMAIL) {
      return;
    }
    connection.setStatus(BroadcastSenderConnectionStatus.DISABLED);
    connection.setSendingEnabled(false);
    connection.setEncryptedCredentials(null);
    connections.save(connection);
    var entries = senders.findByConnectionId(connection.getId());
    entries.forEach(sender -> sender.setStatus(BroadcastSenderStatus.DISABLED));
    senders.saveAll(entries);
  }
}

package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.BroadcastSenderResponse;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.broadcast.repository.BroadcastSenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps sender connection + sender creation atomic after external provider validation succeeds.
 *
 * <p>No AWS network call belongs in this transaction.
 */
@Service
@RequiredArgsConstructor
public class BroadcastSenderPersistenceService {

  private final BroadcastSenderConnectionRepository connectionRepository;
  private final BroadcastSenderRepository senderRepository;

  @Transactional
  public BroadcastSenderResponse create(
      BroadcastSenderConnection connection, BroadcastSender sender) {
    BroadcastSenderConnection savedConnection = connectionRepository.save(connection);
    sender.setConnectionId(savedConnection.getId());
    BroadcastSender savedSender = senderRepository.save(sender);
    return BroadcastSenderResponse.from(savedSender, savedConnection);
  }
}

package com.messaging.broadcast.transport;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;

public interface BroadcastSmsTransport {

  BroadcastSenderProvider provider();

  BroadcastSmsResult send(
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastSmsCommand command);
}

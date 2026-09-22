package com.messaging.broadcast.transport;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.msg91.Msg91Gateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Msg91BroadcastSmsTransport implements BroadcastSmsTransport {

  private final Msg91Gateway gateway;

  @Override
  public BroadcastSenderProvider provider() {
    return BroadcastSenderProvider.MSG91;
  }

  @Override
  public BroadcastSmsResult send(
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastSmsCommand command) {
    return gateway.send(connection, sender, command);
  }
}

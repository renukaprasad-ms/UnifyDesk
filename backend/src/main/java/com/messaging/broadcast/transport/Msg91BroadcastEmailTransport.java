package com.messaging.broadcast.transport;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.msg91.Msg91EmailGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Msg91BroadcastEmailTransport implements BroadcastEmailTransport {

  private final Msg91EmailGateway gateway;

  @Override
  public BroadcastSenderProvider provider() {
    return BroadcastSenderProvider.MSG91_EMAIL;
  }

  @Override
  public BroadcastEmailResult send(
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastEmailCommand command) {
    return gateway.send(connection, sender, command);
  }
}

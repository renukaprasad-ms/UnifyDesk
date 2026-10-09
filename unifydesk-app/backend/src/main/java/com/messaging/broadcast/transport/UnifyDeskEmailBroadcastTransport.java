package com.messaging.broadcast.transport;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.integrations.unifydeskemail.UnifyDeskEmailGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UnifyDeskEmailBroadcastTransport implements BroadcastEmailTransport {
  private final UnifyDeskEmailGateway gateway;

  @Override
  public BroadcastSenderProvider provider() {
    return BroadcastSenderProvider.UNIFYDESK_EMAIL;
  }

  @Override
  public BroadcastEmailResult send(
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastEmailCommand command) {
    return gateway.send(connection, sender, command);
  }
}

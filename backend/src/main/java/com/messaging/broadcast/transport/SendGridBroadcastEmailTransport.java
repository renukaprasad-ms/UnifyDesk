package com.messaging.broadcast.transport;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.sendgrid.SendGridGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SendGridBroadcastEmailTransport implements BroadcastEmailTransport {

  private final SendGridGateway gateway;

  @Override
  public BroadcastSenderProvider provider() {
    return BroadcastSenderProvider.SENDGRID;
  }

  @Override
  public BroadcastEmailResult send(
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastEmailCommand command) {
    return gateway.send(connection, sender, command);
  }
}

package com.messaging.broadcast.transport;

import com.messaging.broadcast.azure.AzureEmailGateway;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureEmailBroadcastEmailTransport implements BroadcastEmailTransport {

  private final AzureEmailGateway gateway;

  @Override
  public BroadcastSenderProvider provider() {
    return BroadcastSenderProvider.AZURE_EMAIL;
  }

  @Override
  public BroadcastEmailResult send(
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastEmailCommand command) {
    return gateway.send(connection, sender, command);
  }
}

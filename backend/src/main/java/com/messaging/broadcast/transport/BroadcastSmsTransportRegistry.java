package com.messaging.broadcast.transport;

import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.common.exception.BadRequestException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BroadcastSmsTransportRegistry {

  private final Map<BroadcastSenderProvider, BroadcastSmsTransport> transports;

  public BroadcastSmsTransportRegistry(List<BroadcastSmsTransport> transports) {
    EnumMap<BroadcastSenderProvider, BroadcastSmsTransport> mapped =
        new EnumMap<>(BroadcastSenderProvider.class);
    for (BroadcastSmsTransport transport : transports) {
      BroadcastSmsTransport previous = mapped.put(transport.provider(), transport);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate broadcast SMS transport for " + transport.provider());
      }
    }
    this.transports = Collections.unmodifiableMap(mapped);
  }

  public BroadcastSmsTransport require(BroadcastSenderProvider provider) {
    BroadcastSmsTransport transport = transports.get(provider);
    if (transport == null) {
      throw new BadRequestException(
          "SMS Broadcast delivery is not supported for provider " + provider);
    }
    return transport;
  }
}

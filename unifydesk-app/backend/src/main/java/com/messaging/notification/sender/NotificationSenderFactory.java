package com.messaging.notification.sender;

import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.notification.model.NotificationChannel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationSenderFactory {

  private final Map<NotificationChannel, NotificationSender> senders;

  public NotificationSenderFactory(List<NotificationSender> senders) {
    this.senders = new EnumMap<>(NotificationChannel.class);
    senders.forEach(sender -> this.senders.put(sender.channel(), sender));
  }

  public NotificationSender get(NotificationChannel channel) {
    NotificationSender sender = senders.get(channel);
    if (sender == null) {
      throw new ServiceUnavailableException("Notification channel is not configured: " + channel);
    }
    return sender;
  }
}

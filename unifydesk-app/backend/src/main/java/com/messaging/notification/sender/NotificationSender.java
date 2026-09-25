package com.messaging.notification.sender;

import com.messaging.notification.dto.SendNotificationRequest;
import com.messaging.notification.model.NotificationChannel;

public interface NotificationSender {

  NotificationChannel channel();

  void send(SendNotificationRequest request);
}

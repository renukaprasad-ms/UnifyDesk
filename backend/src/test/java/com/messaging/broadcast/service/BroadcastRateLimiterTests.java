package com.messaging.broadcast.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import org.junit.jupiter.api.Test;

class BroadcastRateLimiterTests {

  @Test
  void sendGridUsesConfiguredProviderCap() {
    BroadcastProperties properties = new BroadcastProperties();
    properties.setSendGridMaxPerSecondCap(20.0);
    properties.setProviderMaxPerSecondCap(1000.0);

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setProvider(BroadcastSenderProvider.SENDGRID);
    connection.setMaxSendRate(50.0);

    BroadcastRateLimiter limiter = new BroadcastRateLimiter(null, properties);

    assertEquals(20.0, limiter.effectiveRate(connection));
  }

  @Test
  void sesStillHonorsAccountRateWhenLowerThanSafetyCap() {
    BroadcastProperties properties = new BroadcastProperties();
    properties.setSesMaxPerSecondCap(1000.0);

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setProvider(BroadcastSenderProvider.AMAZON_SES);
    connection.setMaxSendRate(14.0);

    BroadcastRateLimiter limiter = new BroadcastRateLimiter(null, properties);

    assertEquals(14.0, limiter.effectiveRate(connection));
  }

  @Test
  void azureEmailUsesConfiguredSafetyCap() {
    BroadcastProperties properties = new BroadcastProperties();
    properties.setAzureEmailMaxPerSecondCap(0.5);

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setProvider(BroadcastSenderProvider.AZURE_EMAIL);
    connection.setMaxSendRate(null);

    BroadcastRateLimiter limiter = new BroadcastRateLimiter(null, properties);

    assertEquals(0.5, limiter.effectiveRate(connection));
  }

  @Test
  void msg91SmsUsesConfiguredSafetyCap() {
    BroadcastProperties properties = new BroadcastProperties();
    properties.setMsg91MaxPerSecondCap(20.0);

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setProvider(BroadcastSenderProvider.MSG91_SMS);
    connection.setMaxSendRate(50.0);

    BroadcastRateLimiter limiter = new BroadcastRateLimiter(null, properties);

    assertEquals(20.0, limiter.effectiveRate(connection));
  }

  @Test
  void msg91EmailUsesConfiguredSafetyCap() {
    BroadcastProperties properties = new BroadcastProperties();
    properties.setMsg91EmailMaxPerSecondCap(12.0);

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setProvider(BroadcastSenderProvider.MSG91_EMAIL);
    connection.setMaxSendRate(50.0);

    BroadcastRateLimiter limiter = new BroadcastRateLimiter(null, properties);

    assertEquals(12.0, limiter.effectiveRate(connection));
  }
}

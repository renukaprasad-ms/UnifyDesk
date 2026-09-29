package com.messaging.channel.meta.runtime;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.channel.meta-whatsapp.runtime")
public class MetaWhatsAppRuntimeProperties {
  private DataSize webhookMaxBody = DataSize.ofMegabytes(1);
  private int webhookMaxEffects = 200;
  private int workerBatchSize = 100;
  private Duration workerLease = Duration.ofSeconds(60);
  private int workerMaxAttempts = 10;
  private Duration retryBaseDelay = Duration.ofSeconds(2);
  private Duration retryMaxDelay = Duration.ofMinutes(15);
  private Duration processedRetention = Duration.ofDays(7);
  private Duration deadRetention = Duration.ofDays(30);
  private Duration customerServiceWindow = Duration.ofHours(24);
  private Duration templateMaxAge = Duration.ofMinutes(15);
  private Duration providerConnectTimeout = Duration.ofSeconds(3);
  private Duration providerReadTimeout = Duration.ofSeconds(10);
  private DataSize inboundMediaHardLimit = DataSize.ofMegabytes(20);
  private String graphBaseUrl = "https://graph.facebook.com";
}

package com.messaging.channel.meta.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MetaWhatsAppWebhookSecrets {
  private final String appSecret;
  private final String verifyToken;

  public MetaWhatsAppWebhookSecrets(
      @Value("${app.channel.meta-whatsapp.app-secret:}") String appSecret,
      @Value("${app.channel.meta-whatsapp.webhook-verify-token:}") String verifyToken) {
    this.appSecret = appSecret == null ? "" : appSecret;
    this.verifyToken = verifyToken == null ? "" : verifyToken;
  }

  public String appSecret() {
    return appSecret;
  }

  public String verifyToken() {
    return verifyToken;
  }
}

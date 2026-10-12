package com.messaging.integrations.unifydeskemail;

import com.messaging.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UnifyDeskEmailFeatureGate {
  private final boolean enabled;

  public UnifyDeskEmailFeatureGate(
      @Value("${APP_UNIFYDESK_EMAIL_ENABLED:false}") boolean enabled) {
    this.enabled = enabled;
  }

  public void requireEnabled() {
    if (!enabled) {
      throw new BadRequestException(
          "UnifyDesk Email provider is not enabled for this environment");
    }
  }

  public boolean enabled() { return enabled; }
}

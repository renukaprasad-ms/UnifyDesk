package com.messaging.managedsms.metareach;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter
@Component
@ConfigurationProperties(prefix = "app.managed-sms.metareach")
public class MetaReachProperties {
  private boolean enabled = false;
  private boolean contractConfirmed = false;
  private String apiKey = "";
  private String routeId = "1";

  public boolean ready() {
    return enabled && contractConfirmed && apiKey != null && !apiKey.isBlank()
        && routeId != null && routeId.matches("[0-9]{1,3}");
  }
  public String accountKey() { return "metareach:master"; }
}

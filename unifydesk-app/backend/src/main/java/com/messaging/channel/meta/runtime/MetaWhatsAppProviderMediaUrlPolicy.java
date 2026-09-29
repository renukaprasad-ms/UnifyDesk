package com.messaging.channel.meta.runtime;

import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class MetaWhatsAppProviderMediaUrlPolicy {

  public void requireTrusted(String url) {
    final URI uri;
    try {
      uri = URI.create(url);
    } catch (RuntimeException exception) {
      throw invalid();
    }
    String host = uri.getHost();
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || host == null
        || uri.getUserInfo() != null
        || !(ownedHost(host, "facebook.com")
            || ownedHost(host, "fbcdn.net")
            || ownedHost(host, "whatsapp.net")
            || ownedHost(host, "fbsbx.com"))) {
      throw invalid();
    }
  }

  private boolean ownedHost(String host, String suffix) {
    String normalized = host.toLowerCase(java.util.Locale.ROOT);
    return normalized.equals(suffix) || normalized.endsWith("." + suffix);
  }

  private MetaWhatsAppRuntimeException invalid() {
    return new MetaWhatsAppRuntimeException(
        "WHATSAPP_MEDIA_INVALID", false, "Provider media URL is not trusted");
  }
}

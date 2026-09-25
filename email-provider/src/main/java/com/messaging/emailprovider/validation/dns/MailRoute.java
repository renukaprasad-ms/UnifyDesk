package com.messaging.emailprovider.validation.dns;

import java.util.List;

public record MailRoute(
    boolean routable,
    boolean nullMx,
    boolean mxFound,
    boolean lookupFailed,
    List<String> hosts) {

  public static MailRoute failed() {
    return new MailRoute(false, false, false, true, List.of());
  }
}

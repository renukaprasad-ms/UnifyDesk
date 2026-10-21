package com.messaging.emailprovider.validation.core;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RoleAddressPolicy {

  private static final Set<String> ROLE_LOCAL_PARTS =
      Set.of(
          "abuse", "admin", "billing", "contact", "help", "hostmaster", "info", "marketing",
          "noreply", "no-reply", "postmaster", "privacy", "sales", "security", "support",
          "webmaster");

  public boolean isRoleAddress(String localPart) {
    return ROLE_LOCAL_PARTS.contains(localPart.toLowerCase(Locale.ROOT));
  }
}

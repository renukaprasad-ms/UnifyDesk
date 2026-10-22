package com.messaging.emailprovider.product.console;

import java.util.Set;

/** Organization capabilities never imply personal mailbox content access. */
public enum ConsolePermission {
  MANAGE_PROJECTS,
  MANAGE_DOMAINS,
  MANAGE_API_KEYS,
  MANAGE_WEBHOOKS,
  MANAGE_MAILBOXES;

  public static Set<ConsolePermission> defaults(ConsoleRole role) {
    return role.canManage() ? Set.of(values()) : Set.of();
  }
}

package com.messaging.emailprovider.product.console;

public enum ConsoleRole {
  OWNER, ADMIN, VIEWER;

  public boolean canManage() {
    return this == OWNER || this == ADMIN;
  }
}

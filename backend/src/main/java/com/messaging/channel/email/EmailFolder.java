package com.messaging.channel.email;

public enum EmailFolder {
  INBOX,
  SENT,
  ALL;

  public String providerKey() {
    return name();
  }

  public String microsoftFolderKey() {
    return switch (this) {
      case INBOX -> "inbox";
      case SENT -> "sentitems";
      case ALL -> throw new IllegalStateException("ALL is not a Microsoft Graph folder");
    };
  }

  public String gmailSearchToken() {
    return switch (this) {
      case INBOX -> "in:inbox";
      case SENT -> "in:sent";
      case ALL -> "in:anywhere";
    };
  }
}

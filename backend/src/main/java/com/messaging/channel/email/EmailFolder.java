package com.messaging.channel.email;

public enum EmailFolder {
  INBOX,
  SENT;

  public String providerKey() {
    return name();
  }

  public String microsoftFolderKey() {
    return this == SENT ? "sentitems" : "inbox";
  }

  public String gmailSearchToken() {
    return this == SENT ? "in:sent" : "in:inbox";
  }
}

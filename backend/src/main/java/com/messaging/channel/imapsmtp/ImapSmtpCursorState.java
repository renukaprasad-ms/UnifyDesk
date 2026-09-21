package com.messaging.channel.imapsmtp;

public record ImapSmtpCursorState(FolderCursor inbox, FolderCursor sent) {

  public static ImapSmtpCursorState empty() {
    return new ImapSmtpCursorState(new FolderCursor(0, 0), new FolderCursor(0, 0));
  }

  public FolderCursor cursor(com.messaging.channel.email.EmailFolder folder) {
    return folder == com.messaging.channel.email.EmailFolder.SENT ? sent : inbox;
  }

  public ImapSmtpCursorState with(
      com.messaging.channel.email.EmailFolder folder, FolderCursor cursor) {
    return folder == com.messaging.channel.email.EmailFolder.SENT
        ? new ImapSmtpCursorState(inbox, cursor)
        : new ImapSmtpCursorState(cursor, sent);
  }

  public record FolderCursor(long uidValidity, long lastUid) {}
}

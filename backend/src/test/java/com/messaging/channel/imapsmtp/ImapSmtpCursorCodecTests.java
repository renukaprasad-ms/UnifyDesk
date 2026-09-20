package com.messaging.channel.imapsmtp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.imapsmtp.ImapSmtpCursorState.FolderCursor;
import org.junit.jupiter.api.Test;

class ImapSmtpCursorCodecTests {

  private final ImapSmtpCursorCodec codec = new ImapSmtpCursorCodec(new ObjectMapper());

  @Test
  void roundTripsInboxAndSentCursors() {
    ImapSmtpCursorState original =
        new ImapSmtpCursorState(new FolderCursor(42, 1001), new FolderCursor(43, 2002));

    ImapSmtpCursorState decoded = codec.decode(codec.encode(original));

    assertThat(decoded.inbox()).isEqualTo(original.inbox());
    assertThat(decoded.sent()).isEqualTo(original.sent());
    assertThat(decoded.cursor(EmailFolder.INBOX).lastUid()).isEqualTo(1001);
    assertThat(decoded.cursor(EmailFolder.SENT).lastUid()).isEqualTo(2002);
  }

  @Test
  void invalidCursorFallsBackToEmpty() {
    ImapSmtpCursorState decoded = codec.decode("not-json");

    assertThat(decoded.inbox().uidValidity()).isZero();
    assertThat(decoded.sent().lastUid()).isZero();
  }
}

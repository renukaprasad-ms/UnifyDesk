package com.messaging.channel.email;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record EmailSendCommand(
    List<String> to,
    List<String> cc,
    List<String> bcc,
    String subject,
    String bodyText,
    String threadId,
    String replyToMessageId,
    boolean replyAll,
    String inReplyTo,
    String references) {

  public EmailSendCommand {
    to = immutable(to);
    cc = immutable(cc);
    bcc = immutable(bcc);
  }

  private static List<String> immutable(List<String> values) {
    if (values == null || values.isEmpty()) return List.of();
    return Collections.unmodifiableList(new ArrayList<>(values));
  }
}

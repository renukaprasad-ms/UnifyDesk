package com.messaging.emailprovider.mailbox.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class MailboxMimeBuilderTests {
  private final MailboxMimeBuilder builder = new MailboxMimeBuilder();
  private static final Instant TIME = Instant.parse("2026-10-09T10:00:00Z");

  @Test
  void producesDeterministicUtf8PlaintextAndNeverListsBcc() {
    var m = new MailboxMimeBuilder.Message(12345L, "example.com",
        "support@example.com", List.of("alice@example.net"),
        List.of("bob@example.net"), "Quarterly ☀️ update", "Hello équipe\nLine 2",
        TIME, "<original@example.net>");
    String mail = new String(builder.build(m), StandardCharsets.ISO_8859_1);
    assertTrue(mail.contains("From: support@example.com\r\n"));
    assertTrue(mail.contains("To: alice@example.net\r\n"));
    assertTrue(mail.contains("Cc: bob@example.net\r\n"));
    assertTrue(mail.contains("Message-ID: <12345@example.com>\r\n"));
    assertTrue(mail.contains("In-Reply-To: <original@example.net>\r\n"));
    assertTrue(mail.contains("Content-Transfer-Encoding: base64\r\n"));
    assertFalse(mail.contains("<script"));
    String encodedBody=mail.substring(mail.indexOf("\r\n\r\n")+4).trim();
    assertEquals("Hello équipe\nLine 2",
        new String(Base64.getMimeDecoder().decode(encodedBody),StandardCharsets.UTF_8));
    assertEquals(mail,new String(builder.build(m), StandardCharsets.ISO_8859_1));
  }

  @Test
  void supportsBccOnlyWithoutRecipientDisclosure() {
    var m=new MailboxMimeBuilder.Message(999L,"example.com","support@example.com",
        List.of(),List.of(),"Private","Hello",TIME,null);
    String mail=new String(builder.build(m),StandardCharsets.ISO_8859_1);
    assertTrue(mail.contains("To: undisclosed-recipients:;\r\n"));
    assertFalse(mail.contains("\r\nBcc:"));
  }

  @Test
  void subjectLinesStayWithinSafeEncodedWordLengths() {
    var m=new MailboxMimeBuilder.Message(99L,"example.com","support@example.com",
        List.of("a@example.net"),List.of(),"✅".repeat(220),"body",TIME,null);
    String mail=new String(builder.build(m),StandardCharsets.ISO_8859_1);
    for(String line:mail.substring(0,mail.indexOf("\r\n\r\n")).split("\r\n")) {
      assertTrue(line.length()<=998,"Header line exceeded maximum");
      if(line.contains("=?UTF-8?B?")) {
        for(String word:line.trim().split(" ")) {
          if(word.contains("=?UTF-8?B?")) assertTrue(word.length()<=75);
        }
      }
    }
  }

  @Test
  void rejectsHeaderInjectionAndOversizedDrafts() {
    assertThrows(IllegalArgumentException.class,
        ()->builder.build(new MailboxMimeBuilder.Message(1L,"example.com",
            "support@example.com",List.of(),List.of(),"Hi\r\nBcc:evil@x.com",
            "body",TIME,null)));
    assertThrows(IllegalArgumentException.class,
        ()->builder.build(new MailboxMimeBuilder.Message(1L,"example.com",
            "support@example.com",List.of(),List.of(),"Hello",
            "x".repeat(65537),TIME,null)));
  }
}

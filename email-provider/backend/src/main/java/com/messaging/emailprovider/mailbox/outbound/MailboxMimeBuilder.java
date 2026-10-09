package com.messaging.emailprovider.mailbox.outbound;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Component;

/** Deterministic RFC 5322 text-only MIME, independent of marketing templates. */
@Component
public class MailboxMimeBuilder {
  public record Message(long intentId,String domain,String from,List<String> to,
      List<String> cc,String subject,String body,Instant acceptedAt,String replyMessageId) {}

  public byte[] build(Message mail) {
    StringBuilder mime=new StringBuilder(1024+mail.body().length()*2);
    mime.append("From: ").append(mail.from()).append("\r\n");
    addresses(mime,"To",mail.to(),true);
    addresses(mime,"Cc",mail.cc(),false);
    subject(mime,mail.subject());
    mime.append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME
        .format(mail.acceptedAt().atZone(ZoneOffset.UTC))).append("\r\n");
    mime.append("Message-ID: <").append(mail.intentId()).append("@")
        .append(mail.domain()).append(">\r\n");
    if (mail.replyMessageId()!=null && mail.replyMessageId()
        .matches("<[A-Za-z0-9._%+-]{1,128}@[A-Za-z0-9.-]{1,128}>")) {
      mime.append("In-Reply-To: ").append(mail.replyMessageId()).append("\r\n");
      mime.append("References: ").append(mail.replyMessageId()).append("\r\n");
    }
    mime.append("MIME-Version: 1.0\r\n")
        .append("Content-Type: text/plain; charset=UTF-8\r\n")
        .append("Content-Transfer-Encoding: base64\r\n")
        .append("X-UnifyDesk-Mail-Intent: ").append(mail.intentId()).append("\r\n\r\n");
    mime.append(Base64.getMimeEncoder(76,"\r\n".getBytes(StandardCharsets.US_ASCII))
        .encodeToString(mail.body().getBytes(StandardCharsets.UTF_8))).append("\r\n");
    return mime.toString().getBytes(StandardCharsets.ISO_8859_1);
  }

  private static void addresses(StringBuilder out,String header,List<String> list,boolean required) {
    if(list.isEmpty()) {
      if(required) out.append("To: undisclosed-recipients:;\r\n");
      return;
    }
    out.append(header).append(": ");
    int length=header.length()+2;
    for(int i=0;i<list.size();i++) {
      String address=list.get(i);
      String next=(i==0?"":", ")+address;
      if(length+next.length()>78 && i>0) {
        out.append(",\r\n ").append(address);
        length=1+address.length();
      } else {
        out.append(next);
        length+=next.length();
      }
    }
    out.append("\r\n");
  }

  /** RFC 2047 encoded words capped at 39 UTF-8 bytes, never split a codepoint. */
  private static void subject(StringBuilder out,String value) {
    out.append("Subject: ");
    StringBuilder current=new StringBuilder();
    int size=0;
    for(int i=0;i<value.length();) {
      int cp=value.codePointAt(i);
      String unit=new String(Character.toChars(cp));
      int bytes=unit.getBytes(StandardCharsets.UTF_8).length;
      if(size+bytes>39 && size>0) {
        out.append(encodedWord(current.toString())).append("\r\n ");
        current.setLength(0);
        size=0;
      }
      current.append(unit);
      size+=bytes;
      i+=Character.charCount(cp);
    }
    if(!current.isEmpty()) out.append(encodedWord(current.toString()));
    out.append("\r\n");
  }

  private static String encodedWord(String value) {
    return "=?UTF-8?B?"+Base64.getEncoder().encodeToString(
        value.getBytes(StandardCharsets.UTF_8))+"?=";
  }
}

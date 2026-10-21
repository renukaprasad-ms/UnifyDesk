package com.messaging.emailprovider.mime;

import com.messaging.emailprovider.mail.entity.EmailMessage;
import com.messaging.emailprovider.mail.entity.EmailRecipient;
import com.messaging.emailprovider.mail.enums.EmailRecipientStatus;
import com.messaging.emailprovider.mail.enums.EmailRecipientType;
import com.messaging.emailprovider.product.unsubscribe.UnsubscribeTokenService;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MimeMessageBuilder {
  private final UnsubscribeTokenService unsubscribe;

  @Autowired
  public MimeMessageBuilder(UnsubscribeTokenService unsubscribe) {
    this.unsubscribe = unsubscribe;
  }

  /** Kept for isolated unit tests that only build transactional messages. */
  public MimeMessageBuilder() {
    this.unsubscribe = null;
  }

  public byte[] build(
      EmailMessage message,
      EmailRecipient target,
      List<EmailRecipient> allRecipients,
      String domain) {
    StringBuilder headers = new StringBuilder();
    headers.append("From: ").append(message.getFromAddress()).append("\r\n");

    List<String> to =
        allRecipients.stream()
            .filter(r -> r.getStatus() != EmailRecipientStatus.SUPPRESSED)
            .filter(r -> r.getRecipientType() == EmailRecipientType.TO)
            .map(EmailRecipient::getRecipientAddress)
            .toList();
    List<String> cc =
        allRecipients.stream()
            .filter(r -> r.getStatus() != EmailRecipientStatus.SUPPRESSED)
            .filter(r -> r.getRecipientType() == EmailRecipientType.CC)
            .map(EmailRecipient::getRecipientAddress)
            .toList();

    // Bulk BCC-only sending personalizes the visible recipient without exposing a list.
    appendAddressHeader(
        headers,
        "To",
        to.isEmpty() && target.getRecipientType() == EmailRecipientType.BCC
            ? List.of(target.getRecipientAddress())
            : to,
        true);
    if (!cc.isEmpty()) {
      appendAddressHeader(headers, "Cc", cc, false);
    }
    if (message.getReplyToAddress() != null && !message.getReplyToAddress().isBlank()) {
      headers.append("Reply-To: ").append(message.getReplyToAddress()).append("\r\n");
    }

    appendFoldedHeader(headers, "Subject", encodeHeader(message.getSubject()));
    headers
        .append("Date: ")
        .append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)))
        .append("\r\n");
    headers
        .append("Message-ID: <")
        .append(message.getId())
        .append(".")
        .append(target.getId())
        .append("@")
        .append(domain)
        .append(">\r\n");
    headers.append("MIME-Version: 1.0\r\n");
    headers.append("X-UnifyDesk-Message-ID: ").append(message.getId()).append("\r\n");
    headers.append("X-UnifyDesk-Recipient-ID: ").append(target.getId()).append("\r\n");

    String unsubscribeUrl = null;
    if (message.isMarketing()) {
      if (unsubscribe == null) throw new IllegalStateException("Unsubscribe signer unavailable");
      unsubscribeUrl = unsubscribe.url(message.getId(), target.getId());
      headers.append("List-Unsubscribe: <").append(unsubscribeUrl).append(">\r\n");
      headers.append("List-Unsubscribe-Post: List-Unsubscribe=One-Click\r\n");
    }
    String textBody =
        message.getTextBody() == null
            ? null
            : message.getTextBody()
                + (unsubscribeUrl == null ? "" : "\n\nUnsubscribe: " + unsubscribeUrl);
    String htmlBody =
        message.getHtmlBody() == null
            ? null
            : message.getHtmlBody()
                + (unsubscribeUrl == null
                    ? ""
                    : "<p><a href=\"" + unsubscribeUrl + "\">Unsubscribe</a></p>");
    String body;
    if (textBody != null && htmlBody != null) {
      String boundary =
          "=_unifydesk_" + message.getId() + "_" + UUID.randomUUID().toString().replace("-", "");
      headers
          .append("Content-Type: multipart/alternative; boundary=\"")
          .append(boundary)
          .append("\"\r\n");
      body =
          "--"
              + boundary
              + "\r\n"
              + "Content-Type: text/plain; charset=UTF-8\r\n"
              + "Content-Transfer-Encoding: base64\r\n\r\n"
              + base64(textBody)
              + "\r\n--"
              + boundary
              + "\r\n"
              + "Content-Type: text/html; charset=UTF-8\r\n"
              + "Content-Transfer-Encoding: base64\r\n\r\n"
              + base64(htmlBody)
              + "\r\n--"
              + boundary
              + "--\r\n";
    } else {
      boolean html = htmlBody != null;
      headers
          .append("Content-Type: ")
          .append(html ? "text/html" : "text/plain")
          .append("; charset=UTF-8\r\n");
      headers.append("Content-Transfer-Encoding: base64\r\n");
      body = base64(html ? htmlBody : textBody) + "\r\n";
    }

    return (headers + "\r\n" + body).getBytes(StandardCharsets.ISO_8859_1);
  }

  private void appendAddressHeader(
      StringBuilder headers, String name, List<String> addresses, boolean allowEmpty) {
    if (addresses.isEmpty()) {
      if (allowEmpty) {
        headers.append(name).append(": undisclosed-recipients:;\r\n");
      }
      return;
    }

    headers.append(name).append(": ");
    int lineLength = name.length() + 2;
    for (int index = 0; index < addresses.size(); index++) {
      String token = (index == 0 ? "" : ", ") + addresses.get(index);
      if (lineLength + token.length() > 78) {
        headers.append("\r\n ");
        lineLength = 1;
        token = (index == 0 ? "" : "") + addresses.get(index);
      }
      headers.append(token);
      lineLength += token.length();
    }
    headers.append("\r\n");
  }

  private void appendFoldedHeader(StringBuilder headers, String name, String value) {
    String prefix = name + ": ";
    headers.append(prefix);
    int lineLength = prefix.length();
    int index = 0;
    while (index < value.length()) {
      int available = Math.max(1, 78 - lineLength);
      int end = Math.min(value.length(), index + available);
      headers.append(value, index, end);
      index = end;
      if (index < value.length()) {
        headers.append("\r\n ");
        lineLength = 1;
      }
    }
    headers.append("\r\n");
  }

  private String encodeHeader(String value) {
    if (StandardCharsets.US_ASCII.newEncoder().canEncode(value)) {
      return value;
    }
    return "=?UTF-8?B?"
        + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))
        + "?=";
  }

  private String base64(String value) {
    return Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}

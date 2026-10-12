package com.messaging.emailprovider.mailbox.outbound;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Strict, bounded RFC 3464 single-recipient delivery-status decoder.
 * Accept only messages delivered by the trusted, private local bounce adapter.
 * Reject ambiguous/multiple-recipient DSNs instead of guessing the target.
 */
@Component
@ConditionalOnProperty(prefix="app.mail.feedback",name="enabled",havingValue="true")
public class MailboxDsnProcessor {
  private static final Pattern RECIPIENT=Pattern.compile(
      "(?im)^Final-Recipient:[ \\t]*rfc822;[ \\t]*([^\\s\\r\\n]+)[ \\t]*$");
  private static final Pattern ACTION=Pattern.compile(
      "(?im)^Action:[ \\t]*(failed|delayed|delivered)[ \\t]*$");
  private static final Pattern STATUS=Pattern.compile(
      "(?im)^Status:[ \\t]*([245]\\.[0-9]{1,3}\\.[0-9]{1,3})[ \\t]*$");
  private static final int MAX_DSN_BYTES=65536;

  private final MailBounceAddress bounce;
  private final JdbcTemplate jdbc;
  private final MailboxDeliveryFeedbackService feedback;

  record Recipient(long org,long mailbox,String address) {}
  record Parsed(String address,MailboxDeliveryFeedbackService.Type type,String status) {}

  public MailboxDsnProcessor(MailBounceAddress bounce,JdbcTemplate jdbc,
      MailboxDeliveryFeedbackService feedback) {
    this.bounce=bounce;
    this.jdbc=jdbc;
    this.feedback=feedback;
  }

  public MailboxDeliveryFeedbackService.Result receive(String envelopeTo,byte[] raw) {
    if(raw==null || raw.length==0 || raw.length>MAX_DSN_BYTES) {
      throw new BadRequestException("Bounce status report exceeds permitted size");
    }
    MailBounceAddress.Target target=bounce.verify(envelopeTo);
    Parsed parsed=parse(new String(raw,StandardCharsets.ISO_8859_1));
    List<Recipient> matched=jdbc.query("""
        SELECT organization_id,mailbox_id,recipient_address
        FROM email_mail_outbound_recipients
        WHERE id=? AND intent_id=?
        """,(rs,row)->new Recipient(rs.getLong(1),rs.getLong(2),rs.getString(3)),
        target.recipientId(),target.intentId());
    if(matched.size()!=1 || !matched.get(0).address().equals(parsed.address())) {
      throw new NotFoundException("Delivery report does not match sent recipient");
    }
    Recipient recipient=matched.get(0);
    MailboxDeliveryFeedbackService.Event event=new MailboxDeliveryFeedbackService.Event(
        Long.toString(recipient.org()),Long.toString(recipient.mailbox()),
        Long.toString(target.intentId()),Long.toString(target.recipientId()),
        parsed.address(),eventId(envelopeTo,raw),parsed.type(),parsed.status());
    return feedback.accept(event);
  }

  static Parsed parse(String text) {
    if(text==null || text.length()>MAX_DSN_BYTES) {
      throw new BadRequestException("Invalid delivery-status report");
    }
    String address=single(RECIPIENT,text,"Final-Recipient").toLowerCase(Locale.ROOT);
    String action=single(ACTION,text,"Action").toLowerCase(Locale.ROOT);
    String status=single(STATUS,text,"Status");
    if(!address.matches("[a-z0-9][a-z0-9._%+-]{0,63}@[a-z0-9-]+(\\.[a-z0-9-]+)+")
        || address.length()>320) {
      throw new BadRequestException("Unsupported DSN recipient");
    }
    MailboxDeliveryFeedbackService.Type type;
    if(action.equals("failed") && status.startsWith("5.")) {
      type=MailboxDeliveryFeedbackService.Type.BOUNCED;
    } else if(action.equals("delayed") && status.startsWith("4.")) {
      type=MailboxDeliveryFeedbackService.Type.DEFERRED;
    } else if(action.equals("delivered") && status.startsWith("2.")) {
      type=MailboxDeliveryFeedbackService.Type.DELIVERED;
    } else {
      throw new BadRequestException("Contradictory DSN action or status");
    }
    return new Parsed(address,type,status);
  }

  private static String single(Pattern pattern,String body,String label) {
    Matcher matcher=pattern.matcher(body);
    if(!matcher.find()) throw new BadRequestException("Missing "+label);
    String value=matcher.group(1);
    if(matcher.find()) throw new BadRequestException("Ambiguous "+label);
    return value;
  }

  private static String eventId(String recipient,byte[] message) {
    try {
      MessageDigest digest=MessageDigest.getInstance("SHA-256");
      digest.update(recipient.toLowerCase(Locale.ROOT)
          .getBytes(StandardCharsets.US_ASCII));
      digest.update((byte)0);
      digest.update(message);
      return "dsn-"+HexFormat.of().formatHex(digest.digest());
    } catch(NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable",unavailable);
    }
  }
}

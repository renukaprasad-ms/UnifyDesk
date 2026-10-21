package com.messaging.emailprovider.mailbox.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.emailprovider.smtp.SmtpDeliveryOutcome;
import com.messaging.emailprovider.smtp.SmtpDeliveryResult;
import org.junit.jupiter.api.Test;

class MailboxOutboundWorkerTests {
  @Test
  void neverMapsUncertainSmtpHandoffToRetryable() {
    assertEquals("UNKNOWN",MailboxOutboundWorker.mapOutcome(
        new SmtpDeliveryResult(SmtpDeliveryOutcome.UNKNOWN,null,"lost DATA reply")));
    assertEquals("UNKNOWN",MailboxOutboundWorker.mapOutcome(null));
    assertEquals("DEFERRED",MailboxOutboundWorker.mapOutcome(
        new SmtpDeliveryResult(SmtpDeliveryOutcome.DEFERRED,"450","temporary")));
    assertEquals("FAILED",MailboxOutboundWorker.mapOutcome(
        new SmtpDeliveryResult(SmtpDeliveryOutcome.FAILED,"550","rejected")));
    assertEquals("HANDED_OFF",MailboxOutboundWorker.mapOutcome(
        new SmtpDeliveryResult(SmtpDeliveryOutcome.HANDED_OFF,"250","queued")));
  }

  @Test
  void relayRequiresDedicatedLoopbackAndDistinctSmtpPort() {
    assertThrows(IllegalStateException.class,
        () -> new MailboxSmtpRelay("mail.example.com",2525,25,"mail.example.com"));
    assertThrows(IllegalStateException.class,
        () -> new MailboxSmtpRelay("127.0.0.1",25,25,"mail.example.com"));
    assertThrows(IllegalStateException.class,
        () -> new MailboxSmtpRelay("127.0.0.1",2525,25,"bad\r\ninjection"));
  }

  @Test
  void localRelayRefusesMalformedEnvelopeBeforeNetwork() {
    var relay=new MailboxSmtpRelay("127.0.0.1",2525,25,"mail.example.com");
    assertThrows(IllegalArgumentException.class,
        () -> relay.submit("support@example.com","victim@example.com\r\nDATA",
            new byte[] {65}));
  }
}

package com.messaging.emailprovider.mailbox.outbound;

import com.messaging.emailprovider.smtp.PostfixSmtpClient;
import com.messaging.emailprovider.smtp.SmtpDeliveryResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dedicated local Postfix submission instance, NOT marketing/bulk SMTP. */
@Component
@ConditionalOnProperty(prefix="app.mail.outbound",name="enabled",havingValue="true")
public class MailboxSmtpRelay {
  private final PostfixSmtpClient smtp;

  public MailboxSmtpRelay(
      @Value("${app.mail.outbound.smtp-host:}") String host,
      @Value("${app.mail.outbound.smtp-port:0}") int port,
      @Value("${app.smtp.host:127.0.0.1}") String bulkHost,
      @Value("${app.smtp.port:25}") int bulkPort,
      @Value("${app.mail.outbound.ehlo-host:}") String ehlo) {
    if (!"127.0.0.1".equals(host) && !"localhost".equals(host) && !"::1".equals(host)) {
      throw new IllegalStateException("Mailbox SMTP must use a local private relay");
    }
    if(port<=0 || port>65535
        || (port==bulkPort && (host.equals(bulkHost)
            || ("localhost".equals(host) && "127.0.0.1".equals(bulkHost))))) {
      throw new IllegalStateException("Mailbox SMTP requires a separate local relay port");
    }
    if(ehlo==null || !ehlo.matches("[a-zA-Z0-9.-]{3,253}") || !ehlo.contains(".")
        || ehlo.endsWith(".") || ehlo.contains("..")) {
      throw new IllegalStateException("A valid mailbox EHLO host is required");
    }
    smtp=new PostfixSmtpClient(host,port,5000,10000,ehlo);
  }

  public SmtpDeliveryResult submit(String sender,String recipient,byte[] dkimSignedMime) {
    return smtp.handoff(sender,recipient,dkimSignedMime);
  }
}

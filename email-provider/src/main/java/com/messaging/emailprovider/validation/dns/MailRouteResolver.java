package com.messaging.emailprovider.validation.dns;

public interface MailRouteResolver {
  MailRoute resolve(String domain);
}

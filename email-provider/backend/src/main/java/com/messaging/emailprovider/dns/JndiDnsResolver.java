package com.messaging.emailprovider.dns;

import com.messaging.emailprovider.common.exception.ServiceUnavailableException;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import org.springframework.stereotype.Component;

@Component
public class JndiDnsResolver implements DnsResolver {

  @Override
  public Set<String> findTxtRecords(String name) {
    Hashtable<String, String> environment = new Hashtable<>();
    environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
    environment.put("com.sun.jndi.dns.timeout.initial", "2000");
    environment.put("com.sun.jndi.dns.timeout.retries", "2");

    try {
      InitialDirContext context = new InitialDirContext(environment);
      try {
        Attributes attributes = context.getAttributes(name, new String[] {"TXT"});
        Attribute txt = attributes.get("TXT");
        if (txt == null) {
          return Set.of();
        }

        Set<String> records = new LinkedHashSet<>();
        NamingEnumeration<?> values = txt.getAll();
        while (values.hasMore()) {
          records.add(normalizeTxtValue(String.valueOf(values.next())));
        }
        return Set.copyOf(records);
      } finally {
        context.close();
      }
    } catch (NameNotFoundException exception) {
      return Set.of();
    } catch (NamingException exception) {
      throw new ServiceUnavailableException("DNS lookup failed", exception);
    }
  }

  static String normalizeTxtValue(String value) {
    return value.replace("\"", "").replaceAll("\\s+", " ").trim();
  }
}

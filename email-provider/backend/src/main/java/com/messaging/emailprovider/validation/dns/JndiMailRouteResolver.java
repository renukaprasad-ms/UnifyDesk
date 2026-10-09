package com.messaging.emailprovider.validation.dns;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import org.springframework.stereotype.Component;

@Component
public class JndiMailRouteResolver implements MailRouteResolver {

  @Override
  public MailRoute resolve(String domain) {
    Hashtable<String, String> environment = new Hashtable<>();
    environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
    environment.put("com.sun.jndi.dns.timeout.initial", "2000");
    environment.put("com.sun.jndi.dns.timeout.retries", "2");

    try {
      InitialDirContext context = new InitialDirContext(environment);
      try {
        List<MxRecord> mxRecords = readMx(context, domain);
        if (!mxRecords.isEmpty()) {
          boolean nullMx = mxRecords.size() == 1 && ".".equals(mxRecords.getFirst().host());
          List<String> hosts =
              nullMx
                  ? List.of()
                  : mxRecords.stream()
                      .sorted(Comparator.comparingInt(MxRecord::priority))
                      .map(MxRecord::host)
                      .distinct()
                      .toList();
          return new MailRoute(!nullMx, nullMx, true, false, hosts);
        }

        Attributes fallback = context.getAttributes(domain, new String[] {"A", "AAAA"});
        boolean hasAddress = fallback.get("A") != null || fallback.get("AAAA") != null;
        return new MailRoute(
            hasAddress, false, false, false, hasAddress ? List.of(domain) : List.of());
      } finally {
        context.close();
      }
    } catch (NameNotFoundException exception) {
      return new MailRoute(false, false, false, false, List.of());
    } catch (NamingException exception) {
      return MailRoute.failed();
    }
  }

  private List<MxRecord> readMx(InitialDirContext context, String domain) throws NamingException {
    Attributes attributes = context.getAttributes(domain, new String[] {"MX"});
    Attribute mx = attributes.get("MX");
    if (mx == null) {
      return List.of();
    }

    List<MxRecord> records = new ArrayList<>();
    NamingEnumeration<?> values = mx.getAll();
    while (values.hasMore()) {
      String value = String.valueOf(values.next()).trim();
      String[] parts = value.split("\\s+", 2);
      if (parts.length != 2) {
        continue;
      }
      String host =
          parts[1].endsWith(".") ? parts[1].substring(0, parts[1].length() - 1) : parts[1];
      if (parts[1].equals(".")) {
        host = ".";
      }
      try {
        records.add(new MxRecord(Integer.parseInt(parts[0]), host));
      } catch (NumberFormatException ignored) {
        // Keep processing other MX records.
      }
    }
    return records;
  }

  private record MxRecord(int priority, String host) {}
}

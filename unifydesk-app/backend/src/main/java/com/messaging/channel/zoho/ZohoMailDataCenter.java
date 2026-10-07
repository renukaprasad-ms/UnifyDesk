package com.messaging.channel.zoho;

import com.messaging.common.exception.BadRequestException;
import java.net.URI;
import java.util.Map;

public final class ZohoMailDataCenter {

  private static final Map<String, String> MAIL_BASE_BY_ACCOUNTS_HOST =
      Map.ofEntries(
          Map.entry("accounts.zoho.com", "https://mail.zoho.com"),
          Map.entry("accounts.zoho.eu", "https://mail.zoho.eu"),
          Map.entry("accounts.zoho.in", "https://mail.zoho.in"),
          Map.entry("accounts.zoho.com.au", "https://mail.zoho.com.au"),
          Map.entry("accounts.zoho.jp", "https://mail.zoho.jp"),
          Map.entry("accounts.zohocloud.ca", "https://mail.zohocloud.ca"),
          Map.entry("accounts.zoho.com.cn", "https://mail.zoho.com.cn"),
          Map.entry("accounts.zoho.ae", "https://mail.zoho.ae"),
          Map.entry("accounts.zoho.sa", "https://mail.zoho.sa"));

  private ZohoMailDataCenter() {}

  public static String trustedAccountsServer(String value) {
    URI uri = parse(value);
    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getPort() != -1
        || !MAIL_BASE_BY_ACCOUNTS_HOST.containsKey(host)
        || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))
        || uri.getQuery() != null
        || uri.getFragment() != null) {
      throw new BadRequestException("Zoho returned an unsupported accounts data center");
    }
    return "https://" + host;
  }

  public static String mailBaseUrl(String accountsServer) {
    String trusted = trustedAccountsServer(accountsServer);
    String host = URI.create(trusted).getHost().toLowerCase();
    return MAIL_BASE_BY_ACCOUNTS_HOST.get(host);
  }

  public static String trustedMailBaseUrl(String value) {
    URI uri = parse(value);
    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
    boolean known = MAIL_BASE_BY_ACCOUNTS_HOST.containsValue("https://" + host);
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getPort() != -1
        || !known
        || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))
        || uri.getQuery() != null
        || uri.getFragment() != null) {
      throw new BadRequestException("Zoho Mail returned an unsupported API data center");
    }
    return "https://" + host;
  }

  private static URI parse(String value) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException("Zoho accounts server is missing");
    }
    try {
      return URI.create(value.trim());
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Zoho accounts server is invalid");
    }
  }
}

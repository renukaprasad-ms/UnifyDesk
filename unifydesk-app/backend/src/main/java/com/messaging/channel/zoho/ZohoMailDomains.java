package com.messaging.channel.zoho;

import com.messaging.common.exception.BadRequestException;
import java.util.Locale;
import java.util.Map;

final class ZohoMailDomains {

  private static final Map<String, Region> REGIONS =
      Map.ofEntries(
          Map.entry("us", new Region("https://accounts.zoho.com", "https://mail.zoho.com")),
          Map.entry("in", new Region("https://accounts.zoho.in", "https://mail.zoho.in")),
          Map.entry("eu", new Region("https://accounts.zoho.eu", "https://mail.zoho.eu")),
          Map.entry("au", new Region("https://accounts.zoho.com.au", "https://mail.zoho.com.au")),
          Map.entry("jp", new Region("https://accounts.zoho.jp", "https://mail.zoho.jp")),
          Map.entry("ca", new Region("https://accounts.zohocloud.ca", "https://mail.zohocloud.ca")),
          Map.entry("cn", new Region("https://accounts.zoho.com.cn", "https://mail.zoho.com.cn")),
          Map.entry("ae", new Region("https://accounts.zoho.ae", "https://mail.zoho.ae")),
          Map.entry("sa", new Region("https://accounts.zoho.sa", "https://mail.zoho.sa")));

  private ZohoMailDomains() {}

  static Region resolve(String location, String accountsServer) {
    if (location == null || location.isBlank()) {
      throw new BadRequestException("Zoho did not return the account data-center location");
    }
    Region region = REGIONS.get(location.trim().toLowerCase(Locale.ROOT));
    if (region == null) {
      throw new BadRequestException("Zoho data center is not supported: " + location);
    }

    if (accountsServer != null
        && !accountsServer.isBlank()
        && !region.accountsServer().equalsIgnoreCase(stripTrailingSlash(accountsServer.trim()))) {
      throw new BadRequestException("Zoho accounts server does not match the returned data center");
    }
    return region;
  }

  static String validateAuthorizationBase(String baseUrl) {
    String normalized = stripTrailingSlash(baseUrl == null ? "" : baseUrl.trim());
    boolean supported =
        REGIONS.values().stream()
            .map(Region::accountsServer)
            .anyMatch(value -> value.equalsIgnoreCase(normalized));
    if (!supported) {
      throw new BadRequestException("Zoho authorization accounts server is not supported");
    }
    return normalized;
  }

  private static String stripTrailingSlash(String value) {
    String result = value;
    while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
    return result;
  }

  record Region(String accountsServer, String mailApiBaseUrl) {}
}

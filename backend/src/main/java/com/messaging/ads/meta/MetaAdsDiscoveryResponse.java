package com.messaging.ads.meta;

import java.util.List;

public record MetaAdsDiscoveryResponse(
    String sessionId,
    String userId,
    String userName,
    List<BusinessOption> businesses,
    List<AccountOption> standaloneAccounts) {

  public record BusinessOption(String id, String name, List<AccountOption> accounts) {}

  public record AccountOption(
      String id,
      String accountId,
      String name,
      String currency,
      String timezone,
      Integer accountStatus) {}
}

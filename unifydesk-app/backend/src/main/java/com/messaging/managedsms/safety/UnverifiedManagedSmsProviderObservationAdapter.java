package com.messaging.managedsms.safety;

import com.messaging.managedsms.metareach.MetaReachProperties;
import com.messaging.managedsms.metareach.MetaReachSmsClient;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UnverifiedManagedSmsProviderObservationAdapter
    implements ManagedSmsProviderObservationAdapter {
  private final MetaReachSmsClient metaReach;
  private final MetaReachProperties metaConfig;
  public UnverifiedManagedSmsProviderObservationAdapter(MetaReachSmsClient metaReach,
      MetaReachProperties metaConfig) {
    this.metaReach=metaReach;
    this.metaConfig=metaConfig;
  }
  @Override
  public Optional<ProviderObservation> observe(String accountKey,String providerClientRef) {
    if (!metaConfig.accountKey().equals(accountKey) || !metaConfig.ready()) return Optional.empty();
    return metaReach.balance().map(balance -> {
      Instant now=Instant.now();
      return new ProviderObservation(accountKey,null,balance,null,null,true,
          now,now.plusSeconds(90),"METAREACH_BALANCE");
    });
  }
}

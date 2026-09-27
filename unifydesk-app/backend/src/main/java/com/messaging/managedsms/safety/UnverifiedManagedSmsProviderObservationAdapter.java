package com.messaging.managedsms.safety;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(ManagedSmsProviderObservationAdapter.class)
public class UnverifiedManagedSmsProviderObservationAdapter
    implements ManagedSmsProviderObservationAdapter {

  @Override
  public Optional<ProviderObservation> observe(
      String providerAccountKey, String providerClientRef) {
    // The contracted production MSG91 balance endpoint is not verified. Do not manufacture
    // a healthy observation from configuration or legacy plain-HTTP samples.
    return Optional.empty();
  }
}

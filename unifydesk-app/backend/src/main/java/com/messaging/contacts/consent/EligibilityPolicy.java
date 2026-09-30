package com.messaging.contacts.consent;

import com.messaging.contacts.consent.ConsentTypes.*;
import java.util.Optional;
import org.springframework.stereotype.Component;

public interface EligibilityPolicy {

  boolean requiresAffirmativeOptIn(Channel channel, Purpose purpose, EligibilityContext context);

  Optional<EligibilityReason> additionalBlock(
      long companyId,
      ContactChannelIdentity identity,
      Channel channel,
      Purpose purpose,
      EligibilityContext context);

  String version();

  @Component
  final class DefaultEligibilityPolicy implements EligibilityPolicy {
    @Override
    public boolean requiresAffirmativeOptIn(
        Channel channel, Purpose purpose, EligibilityContext context) {
      return purpose == Purpose.MARKETING;
    }

    @Override
    public Optional<EligibilityReason> additionalBlock(
        long companyId,
        ContactChannelIdentity identity,
        Channel channel,
        Purpose purpose,
        EligibilityContext context) {
      return Optional.empty();
    }

    @Override
    public String version() {
      return "contacts-v1";
    }
  }
}

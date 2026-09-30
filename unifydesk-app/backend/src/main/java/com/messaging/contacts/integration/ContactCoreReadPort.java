package com.messaging.contacts.integration;

import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.ContactChannelIdentity;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ContactCoreReadPort {

  boolean activeContactExists(long companyId, long contactId);

  Optional<ContactChannelIdentity> loadIdentity(
      long companyId, long contactId, Long identityId, Channel channel);

  Map<Long, ContactChannelIdentity> loadDeterministicIdentities(
      long companyId, Collection<Long> contactIds, Channel channel);
}

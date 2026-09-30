package com.messaging.contacts.integration;

import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.ContactChannelIdentity;
import java.util.*;

public interface ContactCoreReadPort {

  Optional<ContactChannelIdentity> loadIdentity(
      long companyId, long contactId, Long identityId, Channel channel);

  Map<Long, ContactChannelIdentity> loadDeterministicIdentities(
      long companyId, Collection<Long> contactIds, Channel channel);
}

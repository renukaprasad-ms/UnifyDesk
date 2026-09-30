package com.messaging.contacts.core.service;

import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import java.util.Optional;

public interface ContactIdentityLookup {

  Optional<ResolvedIdentity> findExact(
      long companyId, IdentityType type, String namespace, String normalizedValue);

  record ResolvedIdentity(long contactId, long identityId, String normalizedValue) {}
}

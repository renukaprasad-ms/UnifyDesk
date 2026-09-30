package com.messaging.contacts.consent;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.integration.ContactCoreReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactConsentTargetValidator {

  private final ContactCoreReadPort coreReadPort;

  public void requireWritableTarget(
      long companyId, long contactId, Long identityId, Channel channel) {
    if (channel == null) {
      throw new BadRequestException("CONTACT_FIELD_INVALID");
    }

    if (identityId == null) {
      if (!coreReadPort.activeContactExists(companyId, contactId)) {
        throw notFound();
      }
      return;
    }

    if (coreReadPort.loadIdentity(companyId, contactId, identityId, channel).isEmpty()) {
      throw notFound();
    }
  }

  private NotFoundException notFound() {
    return new NotFoundException("CONTACT_NOT_FOUND");
  }
}

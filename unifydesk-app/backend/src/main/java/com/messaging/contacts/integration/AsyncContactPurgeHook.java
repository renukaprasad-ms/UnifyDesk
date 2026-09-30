package com.messaging.contacts.integration;

import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.contacts.core.service.ContactPurgeHook;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AsyncContactPurgeHook implements ContactPurgeHook {

  private final AsyncJobService asyncJobs;

  @Override
  public void enqueue(long companyId, long contactId, long actorUserId) {
    asyncJobs.create(
        companyId,
        actorUserId,
        AsyncJobType.CONTACT_DELETE_PURGE,
        Long.toString(contactId),
        null,
        "Contact privacy purge queued");
  }
}

package com.messaging.contacts.processing;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.contacts.audience.AudienceSnapshotService;
import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.contacts.audience.SegmentService;
import org.junit.jupiter.api.Test;

class ContactJobDispatcherTests {

  @Test
  void deniedClaimFailsBeforeWorkerMutationOrExport() {
    AsyncJobService asyncJobs = mock(AsyncJobService.class);
    ContactProcessingAuthorizationPolicy policy =
        mock(ContactProcessingAuthorizationPolicy.class);
    ContactImportWorker importWorker = mock(ContactImportWorker.class);
    ContactExportWorker exportWorker = mock(ContactExportWorker.class);
    ContactBulkWorker bulkWorker = mock(ContactBulkWorker.class);
    ContactDeletePurgeWorker purgeWorker = mock(ContactDeletePurgeWorker.class);
    SegmentService segmentService = mock(SegmentService.class);
    AudienceSnapshotService snapshotService = mock(AudienceSnapshotService.class);
    ContactJobDispatcher dispatcher =
        new ContactJobDispatcher(
            asyncJobs,
            policy,
            importWorker,
            exportWorker,
            bulkWorker,
            purgeWorker,
            segmentService,
            snapshotService,
            new ContactAudienceProperties());
    AsyncJobService.ClaimedAsyncJob job =
        new AsyncJobService.ClaimedAsyncJob(3L, 5L, 7L, "11", null);
    when(policy.authorizeClaim(AsyncJobType.CONTACT_EXPORT, job))
        .thenReturn(
            ContactProcessingAuthorizationPolicy.Decision.denied(
                ContactProcessingAuthorizationPolicy.AUTHORIZATION_REVOKED));

    dispatcher.processClaimedJob(AsyncJobType.CONTACT_EXPORT, job);

    verify(asyncJobs)
        .fail(3L, ContactProcessingAuthorizationPolicy.AUTHORIZATION_REVOKED);
    verify(exportWorker, never()).process(5L, 11L, 3L);
  }
}

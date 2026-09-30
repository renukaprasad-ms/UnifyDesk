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
  void deniedImportTerminalizesResourceBeforeWorker() {
    Fixture fixture = fixture(AsyncJobType.CONTACT_IMPORT);

    fixture.dispatcher().processClaimedJob(AsyncJobType.CONTACT_IMPORT, fixture.job());

    verify(fixture.importRepository()).fail(5L, 11L);
    verify(fixture.asyncJobs())
        .fail(3L, ContactProcessingAuthorizationPolicy.AUTHORIZATION_REVOKED);
    verify(fixture.importWorker(), never()).process(5L, 11L, 3L);
  }

  @Test
  void deniedExportTerminalizesResourceBeforeWorker() {
    Fixture fixture = fixture(AsyncJobType.CONTACT_EXPORT);

    fixture.dispatcher().processClaimedJob(AsyncJobType.CONTACT_EXPORT, fixture.job());

    verify(fixture.exportRepository()).fail(5L, 11L);
    verify(fixture.asyncJobs())
        .fail(3L, ContactProcessingAuthorizationPolicy.AUTHORIZATION_REVOKED);
    verify(fixture.exportWorker(), never()).process(5L, 11L, 3L);
  }

  @Test
  void deniedBulkTerminalizesResourceBeforeWorker() {
    Fixture fixture = fixture(AsyncJobType.CONTACT_BULK);

    fixture.dispatcher().processClaimedJob(AsyncJobType.CONTACT_BULK, fixture.job());

    verify(fixture.bulkRepository()).fail(5L, 11L);
    verify(fixture.asyncJobs())
        .fail(3L, ContactProcessingAuthorizationPolicy.AUTHORIZATION_REVOKED);
    verify(fixture.bulkWorker(), never()).process(5L, 11L, 3L);
  }

  private Fixture fixture(AsyncJobType type) {
    AsyncJobService asyncJobs = mock(AsyncJobService.class);
    ContactProcessingAuthorizationPolicy policy =
        mock(ContactProcessingAuthorizationPolicy.class);
    ContactImportWorker importWorker = mock(ContactImportWorker.class);
    ContactExportWorker exportWorker = mock(ContactExportWorker.class);
    ContactBulkWorker bulkWorker = mock(ContactBulkWorker.class);
    ContactDeletePurgeWorker purgeWorker = mock(ContactDeletePurgeWorker.class);
    ContactImportRepository importRepository = mock(ContactImportRepository.class);
    ContactExportRepository exportRepository = mock(ContactExportRepository.class);
    ContactBulkRepository bulkRepository = mock(ContactBulkRepository.class);
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
            importRepository,
            exportRepository,
            bulkRepository,
            segmentService,
            snapshotService,
            new ContactAudienceProperties());
    AsyncJobService.ClaimedAsyncJob job =
        new AsyncJobService.ClaimedAsyncJob(3L, 5L, 7L, "11", null);
    when(policy.authorizeClaim(type, job))
        .thenReturn(
            ContactProcessingAuthorizationPolicy.Decision.denied(
                ContactProcessingAuthorizationPolicy.AUTHORIZATION_REVOKED));
    return new Fixture(
        dispatcher,
        asyncJobs,
        importWorker,
        exportWorker,
        bulkWorker,
        importRepository,
        exportRepository,
        bulkRepository,
        job);
  }

  private record Fixture(
      ContactJobDispatcher dispatcher,
      AsyncJobService asyncJobs,
      ContactImportWorker importWorker,
      ContactExportWorker exportWorker,
      ContactBulkWorker bulkWorker,
      ContactImportRepository importRepository,
      ContactExportRepository exportRepository,
      ContactBulkRepository bulkRepository,
      AsyncJobService.ClaimedAsyncJob job) {}
}

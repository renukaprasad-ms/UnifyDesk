package com.messaging.emailprovider.product.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.ConflictException;
import com.messaging.emailprovider.mail.dto.EmailAcceptedResponse;
import com.messaging.emailprovider.mail.dto.SendEmailRequest;
import com.messaging.emailprovider.mail.enums.EmailMessageStatus;
import com.messaging.emailprovider.mail.service.EmailMessageService;
import com.messaging.emailprovider.product.api.BulkEmailRequests;
import com.messaging.emailprovider.product.model.EmailBatch;
import com.messaging.emailprovider.product.model.EmailBatchChunk;
import com.messaging.emailprovider.product.repo.EmailBatchChunkRepository;
import com.messaging.emailprovider.product.repo.EmailBatchRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailBatchServiceTests {
  private final EmailBatchRepository batches = mock(EmailBatchRepository.class);
  private final EmailBatchChunkRepository chunks = mock(EmailBatchChunkRepository.class);
  private final EmailMessageService emails = mock(EmailMessageService.class);
  private final ProductAdmissionQuota quota = mock(ProductAdmissionQuota.class);
  private final EmailBatchService service =
      new EmailBatchService(batches, chunks, emails, new ObjectMapper(), quota);

  @Test
  void duplicatesAreRejectedBeforeReservingQuota() {
    var req = request(List.of("a@example.org", "A@example.org"));
    assertThrows(BadRequestException.class, () -> service.submit(10L, "campaign", req));
    verifyNoInteractions(quota);
  }

  @Test
  void idempotencyMismatchReturnsConflict() {
    var existing = new EmailBatch();
    existing.setProjectId(10L);
    existing.setIdempotencyKey("campaign");
    existing.setRequestHash("different");
    when(batches.findByProjectIdAndIdempotencyKey(10L, "campaign"))
        .thenReturn(Optional.of(existing));
    assertThrows(ConflictException.class,
        () -> service.submit(10L, "campaign", request(List.of("a@example.org"))));
    verifyNoInteractions(emails, quota);
  }

  @Test
  void usesBccOnlyForRecipientPrivacy() {
    when(batches.save(any(EmailBatch.class))).thenAnswer(inv -> {
      EmailBatch batch = inv.getArgument(0);
      batch.setId(123L);
      return batch;
    });
    when(emails.accept(eq(10L), anyString(), any(SendEmailRequest.class)))
        .thenReturn(new EmailAcceptedResponse("101", EmailMessageStatus.QUEUED, 2, 0, false));
    when(chunks.findAllByBatchIdOrderByChunkNumber(123L)).thenReturn(List.of());

    var receipt = service.submit(10L, "campaign", request(List.of("a@example.org", "b@example.org")));
    assertEquals(2, receipt.totalRecipients());
    ArgumentCaptor<SendEmailRequest> payload = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(emails).accept(eq(10L), anyString(), payload.capture());
    assertTrue(payload.getValue().to().isEmpty());
    assertTrue(payload.getValue().cc().isEmpty());
    assertEquals(List.of("a@example.org", "b@example.org"), payload.getValue().bcc());
    verify(quota).reserve(10L, "batch:campaign", 2);
  }

  private BulkEmailRequests.NewBatch request(List<String> recipients) {
    return new BulkEmailRequests.NewBatch(
        1L, "sender@example.com", null, "Hello", "Hello there", null, recipients);
  }
}

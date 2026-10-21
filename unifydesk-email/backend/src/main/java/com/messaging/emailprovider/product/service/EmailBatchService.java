package com.messaging.emailprovider.product.service;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.ConflictException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.mail.dto.EmailAcceptedResponse;
import com.messaging.emailprovider.mail.dto.SendEmailRequest;
import com.messaging.emailprovider.mail.service.EmailMessageService;
import com.messaging.emailprovider.product.api.BulkEmailRequests;
import com.messaging.emailprovider.product.api.BulkEmailResponses;
import com.messaging.emailprovider.product.auth.ApiKeyCrypto;
import com.messaging.emailprovider.product.model.EmailBatch;
import com.messaging.emailprovider.product.model.EmailBatchChunk;
import com.messaging.emailprovider.product.repo.EmailBatchChunkRepository;
import com.messaging.emailprovider.product.repo.EmailBatchRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class EmailBatchService {
  private static final int CHUNK_SIZE = 100;
  private final EmailBatchRepository batches;
  private final EmailBatchChunkRepository chunks;
  private final EmailMessageService emails;
  private final JsonMapper mapper;
  private final ProductAdmissionQuota quota;

  public EmailBatchService(
      EmailBatchRepository batches,
      EmailBatchChunkRepository chunks,
      EmailMessageService emails,
      JsonMapper mapper,
      ProductAdmissionQuota quota) {
    this.batches = batches;
    this.chunks = chunks;
    this.emails = emails;
    this.mapper = mapper;
    this.quota = quota;
  }

  @Transactional
  public BulkEmailResponses.Batch submit(
      Long projectId, String idempotencyKey, BulkEmailRequests.NewBatch request) {
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
      throw new BadRequestException("Valid Idempotency-Key (1-128 chars) is required");
    }
    String key = idempotencyKey.trim();
    if (key.length() > 100) throw new BadRequestException("Batch Idempotency-Key too long");
    String hash = hashRequest(request);
    var replay = batches.findByProjectIdAndIdempotencyKey(projectId, key);
    if (replay.isPresent()) {
      if (!replay.get().getRequestHash().equals(hash)) {
        throw new ConflictException("Idempotency-Key was reused with a different batch");
      }
      return describe(projectId, replay.get(), true);
    }

    List<String> addresses =
        request.recipients().stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).toList();
    if (new LinkedHashSet<>(addresses).size() != addresses.size()) {
      throw new BadRequestException("Bulk recipients must be unique");
    }

    // Reserve at API ingress as well as limiting actual Postfix submission per second.
    quota.reserve(projectId, "batch:" + key, addresses.size());

    EmailBatch batch = new EmailBatch();
    batch.setProjectId(projectId);
    batch.setIdempotencyKey(key);
    batch.setRequestHash(hash);
    batch.setTotalRecipients(addresses.size());
    batch.setChunksCount((addresses.size() + CHUNK_SIZE - 1) / CHUNK_SIZE);
    batch = batches.save(batch);
    int suppressed = 0;

    for (int offset = 0; offset < addresses.size(); offset += CHUNK_SIZE) {
      int number = offset / CHUNK_SIZE;
      var payload =
          new SendEmailRequest(
              request.sendingDomainId(),
              request.from(),
              request.replyTo(),
              List.of(),
              List.of(),
              addresses.subList(offset, Math.min(offset + CHUNK_SIZE, addresses.size())),
              request.subject(),
              request.text(),
              request.html(),
              true,
              request.consentReference());
      EmailAcceptedResponse result =
          emails.accept(projectId, "email-batch:" + batch.getId() + ":" + number, payload);
      EmailBatchChunk chunk = new EmailBatchChunk();
      chunk.setBatchId(batch.getId());
      chunk.setChunkNumber(number);
      chunk.setMessageId(Long.parseLong(result.messageId()));
      chunk.setRecipientCount(result.queuedRecipients() + result.suppressedRecipients());
      chunks.save(chunk);
      suppressed += result.suppressedRecipients();
    }
    batch.setSuppressedRecipients(suppressed);
    batches.save(batch);
    return describe(projectId, batch, false);
  }

  @Transactional(readOnly = true)
  public BulkEmailResponses.Batch get(Long projectId, Long batchId) {
    EmailBatch batch =
        batches
            .findByIdAndProjectId(batchId, projectId)
            .orElseThrow(() -> new NotFoundException("Email batch not found"));
    return describe(projectId, batch, false);
  }

  private BulkEmailResponses.Batch describe(Long projectId, EmailBatch batch, boolean replay) {
    List<BulkEmailResponses.Chunk> statuses = new ArrayList<>();
    for (EmailBatchChunk chunk : chunks.findAllByBatchIdOrderByChunkNumber(batch.getId())) {
      var message = emails.get(projectId, chunk.getMessageId());
      statuses.add(
          new BulkEmailResponses.Chunk(
              chunk.getMessageId().toString(), chunk.getRecipientCount(), message.status()));
    }
    return new BulkEmailResponses.Batch(
        batch.getId().toString(),
        batch.getProjectId().toString(),
        batch.getTotalRecipients(),
        batch.getSuppressedRecipients(),
        batch.getChunksCount(),
        replay,
        statuses);
  }

  private String hashRequest(BulkEmailRequests.NewBatch request) {
    try {
      return ApiKeyCrypto.hash(
          new String(mapper.writeValueAsBytes(request), StandardCharsets.UTF_8));
    } catch (JacksonException e) {
      throw new BadRequestException("Unable to validate batch request");
    }
  }
}

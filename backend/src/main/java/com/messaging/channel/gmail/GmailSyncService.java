package com.messaging.channel.gmail;

import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.gmail.GmailApiClient.GmailProfile;
import com.messaging.channel.gmail.GmailApiClient.MessageListResponse;
import com.messaging.channel.gmail.GmailApiClient.MessageReference;
import com.messaging.channel.gmail.entity.GmailSyncBatch;
import com.messaging.channel.gmail.entity.GmailSyncRun;
import com.messaging.channel.gmail.enums.GmailSyncBatchStatus;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.inbox.repository.InboxMessageRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailSyncService {

  private static final int GMAIL_LIST_PAGE_SIZE = 100;
  private static final int MAX_HTTP_BATCH_SIZE = 50;

  private final GmailOAuthProperties properties;
  private final GmailApiClient gmailApiClient;
  private final GmailTokenService tokenService;
  private final InboxMessageRepository messageRepository;
  private final ChannelConnectionRepository connectionRepository;
  private final AsyncJobService asyncJobService;
  private final GmailSyncStateService stateService;
  private final GmailSyncBatchPublisher batchPublisher;
  private final GmailSyncFinalizerService finalizerService;

  public void sync(
      Long companyId, Long connectionId, Long jobId, LocalDate fromDate, LocalDate toDate) {
    validateRange(fromDate, toDate);

    ChannelConnection connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("Gmail connection not found"));

    if (connection.getProviderType() != ProviderType.GMAIL) {
      throw new BadRequestException("Channel connection is not a Gmail account");
    }
    if (connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
      throw new BadRequestException("Gmail connection is not active");
    }

    GmailSyncRun run = stateService.requireRun(jobId);
    if (run.isDiscoveryComplete()) {
      finalizerService.tryFinalize(jobId);
      return;
    }

    asyncJobService.startOrResume(jobId, run.getDiscoveredCount(), "Discovering Gmail messages");

    String accessToken = tokenService.accessToken(connection);
    int maxMessages = Math.max(1, properties.getSyncMaxMessages());
    int batchSize = Math.min(MAX_HTTP_BATCH_SIZE, Math.max(1, properties.getSyncBatchSize()));
    String query = query(fromDate, toDate);

    long discovered = run.getDiscoveredCount();
    long existing = run.getExistingCount();
    int nextBatchIndex = run.getNextBatchIndex();
    int batchCount = run.getBatchCount();
    String pageToken = run.getNextPageToken();
    List<String> carry = new ArrayList<>(stateService.carryIds(jobId));

    boolean fetchNextPage = discovered == 0 || pageToken != null;
    while (fetchNextPage && discovered < maxMessages) {
      int remaining = (int) Math.min(Integer.MAX_VALUE, maxMessages - discovered);
      MessageListResponse page =
          gmailApiClient.listMessages(
              accessToken, Math.min(remaining, GMAIL_LIST_PAGE_SIZE), query, pageToken);

      List<MessageReference> references =
          page.messages() == null
              ? List.of()
              : page.messages().stream()
                  .filter(reference -> reference != null && hasText(reference.id()))
                  .toList();

      discovered += references.size();
      long estimatedTotal =
          Math.min(
              maxMessages,
              Math.max(
                  discovered,
                  page.resultSizeEstimate() == null ? discovered : page.resultSizeEstimate()));
      asyncJobService.setTotal(
          jobId,
          estimatedTotal,
          "Discovered " + discovered + " Gmail messages; preparing batches");

      Set<String> existingIds = existingMessageIds(connectionId, references);
      if (!existingIds.isEmpty()) {
        existing += existingIds.size();
        asyncJobService.incrementProgress(
            jobId, existingIds.size(), "Skipped " + existing + " messages already synced");
      }

      for (MessageReference reference : references) {
        if (existingIds.contains(reference.id())) {
          continue;
        }
        carry.add(reference.id());
        if (carry.size() == batchSize) {
          GmailSyncBatch batch =
              stateService.createBatchIfAbsent(
                  jobId,
                  companyId,
                  connectionId,
                  nextBatchIndex,
                  List.copyOf(carry));
          publishIfNeeded(run, batch);
          nextBatchIndex++;
          batchCount++;
          carry.clear();
        }
      }

      pageToken = page.nextPageToken();
      stateService.checkpoint(
          jobId,
          pageToken,
          nextBatchIndex,
          carry,
          discovered,
          existing,
          batchCount);

      fetchNextPage = hasText(pageToken) && discovered < maxMessages;
    }

    if (!carry.isEmpty()) {
      GmailSyncBatch batch =
          stateService.createBatchIfAbsent(
              jobId, companyId, connectionId, nextBatchIndex, List.copyOf(carry));
      publishIfNeeded(run, batch);
      nextBatchIndex++;
      batchCount++;
      carry.clear();
    }

    GmailProfile profile = gmailApiClient.profile(accessToken);
    stateService.completeDiscovery(
        jobId,
        nextBatchIndex,
        discovered,
        existing,
        batchCount,
        profile.historyId());

    asyncJobService.setTotal(
        jobId,
        discovered,
        batchCount == 0
            ? "All Gmail messages in this range are already synced"
            : "Queued " + batchCount + " Gmail batches");

    finalizerService.tryFinalize(jobId);
  }

  private void publishIfNeeded(GmailSyncRun run, GmailSyncBatch batch) {
    if (batch.getStatus() == GmailSyncBatchStatus.PENDING
        || batch.getStatus() == GmailSyncBatchStatus.RETRY) {
      batchPublisher.publish(run, batch, null);
    }
  }

  private Set<String> existingMessageIds(
      Long connectionId, List<MessageReference> references) {
    List<String> ids =
        references.stream().map(MessageReference::id).filter(this::hasText).distinct().toList();
    if (ids.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(messageRepository.findExistingExternalMessageIds(connectionId, ids));
  }

  private void validateRange(LocalDate fromDate, LocalDate toDate) {
    if (fromDate == null || toDate == null) {
      throw new BadRequestException("Gmail sync fromDate and toDate are required");
    }
    if (fromDate.isAfter(toDate)) {
      throw new BadRequestException("Gmail sync fromDate must be on or before toDate");
    }
    long days = ChronoUnit.DAYS.between(fromDate, toDate);
    if (days > Math.max(1, properties.getSyncMaxRangeDays())) {
      throw new BadRequestException(
          "Gmail sync range cannot exceed " + properties.getSyncMaxRangeDays() + " days");
    }
  }

  private String query(LocalDate fromDate, LocalDate toDate) {
    long startExclusive = fromDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC) - 1;
    long endExclusive = toDate.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
    return "after:" + startExclusive + " before:" + endExclusive;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}

package com.messaging.channel.gmail;

import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.gmail.GmailApiClient.GmailMessage;
import com.messaging.channel.gmail.GmailApiClient.GmailProfile;
import com.messaging.channel.gmail.GmailApiClient.MessageListResponse;
import com.messaging.channel.gmail.GmailApiClient.MessageReference;
import com.messaging.channel.gmail.dto.GmailSyncResult;
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

  private final GmailOAuthProperties properties;
  private final GmailApiClient gmailApiClient;
  private final GmailTokenService tokenService;
  private final GmailInboxPersistenceService persistenceService;
  private final InboxMessageRepository messageRepository;
  private final ChannelConnectionRepository connectionRepository;
  private final AsyncJobService asyncJobService;

  public GmailSyncResult sync(
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

    asyncJobService.start(jobId, 0, "Finding Gmail messages");
    String accessToken = tokenService.accessToken(connection);
    int maxMessages = Math.max(1, properties.getSyncMaxMessages());
    String query = query(fromDate, toDate);

    List<MessageReference> references = new ArrayList<>(Math.min(maxMessages, 1000));
    String pageToken = null;

    do {
      int remaining = maxMessages - references.size();
      MessageListResponse listResponse =
          gmailApiClient.listMessages(accessToken, Math.min(remaining, 100), query, pageToken);

      if (listResponse.messages() != null) {
        references.addAll(listResponse.messages());
      }

      pageToken = listResponse.nextPageToken();
    } while (references.size() < maxMessages && pageToken != null && !pageToken.isBlank());

    Set<String> existingMessageIds = existingMessageIds(connectionId, references);
    List<MessageReference> pendingReferences =
        references.stream()
            .filter(
                reference ->
                    reference != null
                        && reference.id() != null
                        && !existingMessageIds.contains(reference.id()))
            .toList();

    long totalUnits = pendingReferences.size() + 1L;
    asyncJobService.progress(
        jobId,
        0,
        totalUnits,
        references.isEmpty()
            ? "No Gmail messages found in selected range"
            : pendingReferences.isEmpty()
                ? "All Gmail messages in this range are already synced"
                : "Fetching " + pendingReferences.size() + " new Gmail messages");

    List<GmailMessage> messages = new ArrayList<>(pendingReferences.size());
    int processed = 0;
    for (MessageReference reference : pendingReferences) {
      if (reference != null && reference.id() != null && !reference.id().isBlank()) {
        messages.add(gmailApiClient.message(accessToken, reference.id()));
      }
      processed++;
      asyncJobService.progress(
          jobId,
          processed,
          totalUnits,
          "Fetched " + processed + " of " + pendingReferences.size() + " Gmail messages");
    }

    asyncJobService.progress(jobId, processed, totalUnits, "Saving Gmail messages");
    GmailProfile profile = gmailApiClient.profile(accessToken);
    GmailSyncResult result =
        persistenceService.persistFullSync(connection, messages, profile.historyId());

    asyncJobService.complete(
        jobId,
        "Gmail sync complete: "
            + result.createdMessages()
            + " new messages across "
            + result.touchedConversations()
            + " conversations");
    return result;
  }

  private Set<String> existingMessageIds(Long connectionId, List<MessageReference> references) {
    List<String> ids =
        references.stream()
            .filter(reference -> reference != null && reference.id() != null)
            .map(MessageReference::id)
            .distinct()
            .toList();
    if (ids.isEmpty()) {
      return Set.of();
    }

    Set<String> existing = new HashSet<>();
    int batchSize = 500;
    for (int from = 0; from < ids.size(); from += batchSize) {
      int to = Math.min(ids.size(), from + batchSize);
      existing.addAll(
          messageRepository.findExistingExternalMessageIds(connectionId, ids.subList(from, to)));
    }
    return existing;
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
}

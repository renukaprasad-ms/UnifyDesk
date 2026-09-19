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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailSyncService {

  private final GmailOAuthProperties properties;
  private final GmailApiClient gmailApiClient;
  private final GmailTokenService tokenService;
  private final GmailInboxPersistenceService persistenceService;
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

    long totalUnits = references.size() + 1L;
    asyncJobService.progress(
        jobId,
        0,
        totalUnits,
        references.isEmpty()
            ? "No Gmail messages found in selected range"
            : "Fetching " + references.size() + " Gmail messages");

    List<GmailMessage> messages = new ArrayList<>(references.size());
    int processed = 0;
    for (MessageReference reference : references) {
      if (reference != null && reference.id() != null && !reference.id().isBlank()) {
        messages.add(gmailApiClient.message(accessToken, reference.id()));
      }
      processed++;
      asyncJobService.progress(
          jobId,
          processed,
          totalUnits,
          "Fetched " + processed + " of " + references.size() + " Gmail messages");
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
    LocalDate inclusiveEnd = toDate.plusDays(1);
    return "after:" + slashDate(fromDate) + " before:" + slashDate(inclusiveEnd);
  }

  private String slashDate(LocalDate value) {
    return value.getYear() + "/" + value.getMonthValue() + "/" + value.getDayOfMonth();
  }
}

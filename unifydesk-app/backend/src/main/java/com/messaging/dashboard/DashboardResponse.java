package com.messaging.dashboard;

import com.messaging.ads.dto.*;
import com.messaging.broadcast.dto.BroadcastMessageSummaryResponse;
import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.contacts.audience.SegmentApiModel.SegmentResponse;
import com.messaging.contacts.core.api.ContactDtos.*;
import com.messaging.inbox.dto.*;
import com.messaging.managedemail.control.dto.ManagedEmailDtos.OverviewResponse;
import com.messaging.managedemail.metering.ManagedEmailUsageResponse;
import com.messaging.managedemail.reputation.ManagedEmailDeliverabilityResponse;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.StatusResponse;
import com.messaging.managedsms.metering.ManagedSmsDeliveryHealthService.DeliveryHealth;
import com.messaging.managedsms.metering.ManagedSmsUsageService.UsageSummary;
import java.util.List;

public record DashboardResponse(
    Contacts contacts,
    Broadcasts broadcasts,
    InboxPageResponse<InboxConversationResponse> inbox,
    List<ChannelConnectionResponse> connections,
    ManagedEmail managedEmail,
    ManagedSms managedSms,
    Ads ads,
    List<String> unavailableSections) {
  public record Contacts(
      ContactDashboardSummary summary,
      List<ContactSummary> recent,
      List<ContactListResponse> lists,
      List<SegmentResponse> segments) {}

  public record Broadcasts(long total, List<BroadcastMessageSummaryResponse> recent) {}

  public record ManagedEmail(
      OverviewResponse overview,
      ManagedEmailUsageResponse usage,
      ManagedEmailDeliverabilityResponse deliverability) {}

  public record ManagedSms(StatusResponse status, UsageSummary usage, DeliveryHealth health) {}

  public record Ads(
      List<AdProviderConnectionResponse> connections,
      List<AdAccountResponse> accounts,
      List<AdCampaignResponse> campaigns,
      long campaignTotal) {}
}

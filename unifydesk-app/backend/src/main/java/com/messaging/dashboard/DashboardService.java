package com.messaging.dashboard;

import static com.messaging.company.iam.CompanyPermissionKeys.*;

import com.messaging.ads.service.AdCampaignService;
import com.messaging.ads.service.AdCenterService;
import com.messaging.broadcast.dto.BroadcastMessageSummaryResponse;
import com.messaging.broadcast.service.BroadcastMessageService;
import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.enums.ChannelConnectionCapability;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.contacts.audience.SegmentApiMapper;
import com.messaging.contacts.audience.SegmentService;
import com.messaging.contacts.core.api.ContactDtos.SearchRequest;
import com.messaging.contacts.core.domain.ContactTypes.ContactSort;
import com.messaging.contacts.core.domain.ContactTypes.ContactStatus;
import com.messaging.contacts.core.service.ContactCatalogService;
import com.messaging.contacts.core.service.ContactService;
import com.messaging.inbox.service.InboxQueryService;
import com.messaging.managedemail.control.service.ManagedEmailOverviewService;
import com.messaging.managedemail.metering.ManagedEmailUsageService;
import com.messaging.managedemail.reputation.ManagedEmailReputationService;
import com.messaging.managedsms.control.service.ManagedSmsStatusService;
import com.messaging.managedsms.metering.ManagedSmsDeliveryHealthService;
import com.messaging.managedsms.metering.ManagedSmsUsageService;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {
  private final CompanyAuthorizationService authorizationService;
  private final DashboardTasks tasks;
  private final ContactService contactService;
  private final ContactCatalogService catalogService;
  private final SegmentService segmentService;
  private final SegmentApiMapper segmentMapper;
  private final BroadcastMessageService broadcastService;
  private final InboxQueryService inboxService;
  private final ChannelConnectionService channelService;
  private final ManagedEmailOverviewService emailOverview;
  private final ManagedEmailUsageService emailUsage;
  private final ManagedEmailReputationService emailReputation;
  private final ManagedSmsStatusService smsStatus;
  private final ManagedSmsUsageService smsUsage;
  private final ManagedSmsDeliveryHealthService smsHealth;
  private final ManagedSmsRuntimeProperties smsProperties;
  private final ManagedSmsRuntimeRepository smsRuntime;
  private final AdCenterService adCenter;
  private final AdCampaignService adCampaigns;

  public DashboardResponse get(Long companyId, Long userId) {
    return get(companyId, userId, Section.ALL);
  }

  public enum Section {
    ALL,
    CORE,
    CHANNELS,
    ADS
  }

  public DashboardResponse get(Long companyId, Long userId, Section section) {
    boolean core = section == Section.ALL || section == Section.CORE;
    boolean channels = section == Section.ALL || section == Section.CHANNELS;
    // Resolve access on the request thread before scheduling any workspace reads.
    authorizationService.requireActiveMembership(userId, companyId);
    boolean contactsAllowed =
        core && authorizationService.hasPermission(userId, companyId, CONTACT_READ);
    boolean broadcastsAllowed =
        (core || channels) && authorizationService.hasPermission(userId, companyId, BROADCAST_READ);
    boolean emailAllowed =
        (core || channels) && authorizationService.hasPermission(userId, companyId, EMAIL_READ);
    boolean whatsappAllowed =
        (core || channels) && authorizationService.hasPermission(userId, companyId, WHATSAPP_READ);
    boolean smsAllowed =
        channels && authorizationService.hasPermission(userId, companyId, SMS_READ);
    boolean adsAllowed =
        (section == Section.ALL || section == Section.ADS)
            && authorizationService.hasPermission(userId, companyId, ADS_READ);
    var unavailable = ConcurrentHashMap.<String>newKeySet();

    var summary =
        tasks.load(
            companyId,
            "contacts.summary",
            contactsAllowed,
            () -> contactService.dashboardSummary(companyId),
            unavailable);
    var recent =
        tasks.load(
            companyId,
            "contacts.recent",
            contactsAllowed,
            () ->
                contactService
                    .search(
                        companyId,
                        new SearchRequest(
                            null,
                            5,
                            null,
                            ContactStatus.ACTIVE,
                            null,
                            null,
                            null,
                            null,
                            ContactSort.CREATED_DESC))
                    .items(),
            unavailable);
    var lists =
        tasks.load(
            companyId,
            "contacts.lists",
            contactsAllowed,
            () -> catalogService.lists(companyId),
            unavailable);
    var segments =
        tasks.load(
            companyId,
            "contacts.segments",
            contactsAllowed,
            () -> segmentService.list(companyId).stream().map(segmentMapper::toApi).toList(),
            unavailable);
    var histories =
        Arrays.stream(ChannelType.values())
            .map(
                channel ->
                    tasks.load(
                        companyId,
                        "broadcasts." + channel.name(),
                        core && broadcastsAllowed,
                        () ->
                            broadcastService.listByChannel(
                                companyId, channel, PageRequest.of(0, 5)),
                        unavailable))
            .toList();
    var inbox =
        tasks.load(
            companyId,
            "inbox",
            core && (emailAllowed || whatsappAllowed),
            () ->
                inboxService.listConversations(
                    companyId, null, EmailFolder.INBOX, 0, 5, emailAllowed, whatsappAllowed),
            unavailable);
    var connections =
        tasks.load(
            companyId,
            "connections",
            channels && (emailAllowed || whatsappAllowed || smsAllowed || broadcastsAllowed),
            () ->
                visibleConnections(
                    companyId, emailAllowed, whatsappAllowed, smsAllowed, broadcastsAllowed),
            unavailable);
    var overview =
        tasks.load(
            companyId,
            "managedEmail.overview",
            channels && broadcastsAllowed,
            () -> emailOverview.get(companyId),
            unavailable);
    var emailMetering =
        tasks.load(
            companyId,
            "managedEmail.usage",
            channels && broadcastsAllowed,
            () -> emailUsage.summary(companyId),
            unavailable);
    var reputation =
        tasks.load(
            companyId,
            "managedEmail.deliverability",
            channels && broadcastsAllowed,
            () -> emailReputation.current(companyId),
            unavailable);
    var status =
        tasks.load(
            companyId,
            "managedSms.status",
            channels && broadcastsAllowed,
            () -> smsStatus.get(companyId),
            unavailable);
    var smsMetering =
        tasks.load(
            companyId,
            "managedSms.usage",
            channels && broadcastsAllowed,
            () ->
                smsUsage.summary(
                    companyId,
                    smsProperties.providerConfigured()
                        && smsRuntime.tenantSendingEnabled(companyId)),
            unavailable);
    var health =
        tasks.load(
            companyId,
            "managedSms.health",
            channels && broadcastsAllowed,
            () -> smsHealth.get(companyId),
            unavailable);
    var adConnections =
        tasks.load(
            companyId,
            "ads.connections",
            adsAllowed,
            () -> adCenter.connections(companyId),
            unavailable);
    var accounts =
        tasks.load(
            companyId, "ads.accounts", adsAllowed, () -> adCenter.accounts(companyId), unavailable);
    var campaigns =
        tasks.load(
            companyId,
            "ads.campaigns",
            adsAllowed,
            () ->
                adCampaigns.list(
                    companyId, null, PageRequest.of(0, 50, Sort.by("providerUpdatedAt"))),
            unavailable);

    var all =
        new java.util.ArrayList<CompletableFuture<?>>(
            List.of(
                summary,
                recent,
                lists,
                segments,
                inbox,
                connections,
                overview,
                emailMetering,
                reputation,
                status,
                smsMetering,
                health,
                adConnections,
                accounts,
                campaigns));
    all.addAll(histories);
    CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).join();
    var pages =
        histories.stream().map(CompletableFuture::join).filter(java.util.Objects::nonNull).toList();
    var recentBroadcasts =
        pages.stream()
            .flatMap(page -> page.getContent().stream())
            .sorted(Comparator.comparing(BroadcastMessageSummaryResponse::createdAt).reversed())
            .limit(5)
            .toList();
    var campaignPage = campaigns.join();
    return new DashboardResponse(
        new DashboardResponse.Contacts(
            summary.join(),
            emptyIfNull(recent.join()),
            emptyIfNull(lists.join()),
            emptyIfNull(segments.join())),
        new DashboardResponse.Broadcasts(
            pages.stream().mapToLong(page -> page.getTotalElements()).sum(), recentBroadcasts),
        inbox.join(),
        emptyIfNull(connections.join()),
        new DashboardResponse.ManagedEmail(
            overview.join(), emailMetering.join(), reputation.join()),
        new DashboardResponse.ManagedSms(status.join(), smsMetering.join(), health.join()),
        new DashboardResponse.Ads(
            emptyIfNull(adConnections.join()),
            emptyIfNull(accounts.join()),
            campaignPage == null ? List.of() : campaignPage.getContent(),
            campaignPage == null ? 0 : campaignPage.getTotalElements()),
        unavailable.stream().sorted().toList());
  }

  private List<ChannelConnectionResponse> visibleConnections(
      Long companyId, boolean email, boolean whatsapp, boolean sms, boolean broadcast) {
    return Arrays.stream(ChannelType.values())
        .filter(
            type ->
                switch (type) {
                  case EMAIL -> email || broadcast;
                  case WHATSAPP -> whatsapp;
                  case SMS -> sms;
                })
        .flatMap(type -> channelService.list(companyId, type).stream())
        .filter(
            connection ->
                connection.channelType() != ChannelType.EMAIL
                    || (email
                        && connection.capabilities().contains(ChannelConnectionCapability.INBOX))
                    || (broadcast
                        && connection
                            .capabilities()
                            .contains(ChannelConnectionCapability.BROADCAST)))
        .sorted(Comparator.comparing(ChannelConnectionResponse::createdAt).reversed())
        .toList();
  }

  private static <T> List<T> emptyIfNull(List<T> values) {
    return values == null ? List.of() : values;
  }
}

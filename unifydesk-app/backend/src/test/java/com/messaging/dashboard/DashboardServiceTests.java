package com.messaging.dashboard;

import static com.messaging.company.iam.CompanyPermissionKeys.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.messaging.ads.service.*;
import com.messaging.broadcast.service.BroadcastMessageService;
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.contacts.audience.*;
import com.messaging.contacts.core.service.*;
import com.messaging.inbox.service.InboxQueryService;
import com.messaging.managedemail.control.service.ManagedEmailOverviewService;
import com.messaging.managedemail.metering.ManagedEmailUsageService;
import com.messaging.managedemail.reputation.ManagedEmailReputationService;
import com.messaging.managedsms.control.service.ManagedSmsStatusService;
import com.messaging.managedsms.metering.*;
import com.messaging.managedsms.runtime.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTests {
  @Mock CompanyAuthorizationService authorizationService;
  @Spy DashboardTasks tasks = new DashboardTasks(Runnable::run, 1000);
  @Mock ContactService contactService;
  @Mock ContactCatalogService catalogService;
  @Mock SegmentService segmentService;
  @Mock SegmentApiMapper segmentMapper;
  @Mock BroadcastMessageService broadcastService;
  @Mock InboxQueryService inboxService;
  @Mock ChannelConnectionService channelService;
  @Mock ManagedEmailOverviewService emailOverview;
  @Mock ManagedEmailUsageService emailUsage;
  @Mock ManagedEmailReputationService emailReputation;
  @Mock ManagedSmsStatusService smsStatus;
  @Mock ManagedSmsUsageService smsUsage;
  @Mock ManagedSmsDeliveryHealthService smsHealth;
  @Mock ManagedSmsRuntimeProperties smsProperties;
  @Mock ManagedSmsRuntimeRepository smsRuntime;
  @Mock AdCenterService adCenter;
  @Mock AdCampaignService adCampaigns;
  @InjectMocks DashboardService service;

  @Test
  void coreDoesNotScheduleManagedChannelsOrAdvertisingEvenForAFullAccessMember() {
    when(authorizationService.hasPermission(eq(2L), eq(1L), anyString())).thenReturn(true);
    when(contactService.search(eq(1L), any()))
        .thenReturn(
            new com.messaging.contacts.core.api.ContactDtos.ContactPage<>(
                java.util.List.of(), null, false));
    var result = service.get(1L, 2L, DashboardService.Section.CORE);
    verify(contactService).dashboardSummary(1L);
    verify(inboxService)
        .listConversations(
            1L, null, com.messaging.channel.email.EmailFolder.INBOX, 0, 5, true, true);
    assertThat(result.unavailableSections()).isEmpty();
    verifyNoInteractions(
        channelService,
        emailOverview,
        emailUsage,
        emailReputation,
        smsStatus,
        smsUsage,
        smsHealth,
        smsRuntime,
        adCenter,
        adCampaigns);
  }

  @Test
  void channelGroupNeverLoadsCoreOrAds() {
    when(authorizationService.hasPermission(eq(2L), eq(1L), anyString())).thenReturn(true);
    service.get(1L, 2L, DashboardService.Section.CHANNELS);
    verify(emailOverview).get(1L);
    verify(smsStatus).get(1L);
    verifyNoInteractions(
        contactService,
        catalogService,
        segmentService,
        broadcastService,
        inboxService,
        adCenter,
        adCampaigns);
  }

  @Test
  void adsGroupNeverLoadsCoreOrChannels() {
    when(authorizationService.hasPermission(2L, 1L, ADS_READ)).thenReturn(true);
    service.get(1L, 2L, DashboardService.Section.ADS);
    verify(adCenter).accounts(1L);
    verify(adCenter).connections(1L);
    verifyNoInteractions(
        contactService,
        catalogService,
        segmentService,
        broadcastService,
        inboxService,
        channelService,
        emailOverview,
        emailUsage,
        emailReputation,
        smsStatus,
        smsUsage,
        smsHealth,
        smsRuntime);
  }

  @Test
  void inaccessibleWorkspaceFailsBeforeAnyReadsAreSubmitted() {
    doThrow(new ForbiddenException("Company access denied"))
        .when(authorizationService)
        .requireActiveMembership(2L, 1L);
    assertThatThrownBy(() -> service.get(1L, 2L)).isInstanceOf(ForbiddenException.class);
    verifyNoInteractions(tasks, contactService, broadcastService, inboxService, channelService);
  }

  @Test
  void memberWithoutModulePermissionsReceivesNoRestrictedData() {
    var result = service.get(1L, 2L);
    assertThat(result.contacts().summary()).isNull();
    assertThat(result.contacts().recent()).isEmpty();
    assertThat(result.broadcasts().recent()).isEmpty();
    assertThat(result.connections()).isEmpty();
    assertThat(result.inbox()).isNull();
    assertThat(result.ads().accounts()).isEmpty();
    assertThat(result.unavailableSections()).isEmpty();
    verifyNoInteractions(
        contactService,
        catalogService,
        segmentService,
        broadcastService,
        inboxService,
        channelService,
        emailOverview,
        emailUsage,
        emailReputation,
        smsStatus,
        smsUsage,
        smsHealth,
        smsRuntime,
        adCenter,
        adCampaigns);
  }

  @Test
  void inboxReceivesOnlyTheChannelPermissionsOfTheCurrentWorkspace() {
    when(authorizationService.hasPermission(eq(2L), eq(1L), anyString()))
        .thenAnswer(invocation -> WHATSAPP_READ.equals(invocation.getArgument(2)));
    service.get(1L, 2L);
    verify(inboxService)
        .listConversations(
            1L, null, com.messaging.channel.email.EmailFolder.INBOX, 0, 5, false, true);
    verify(channelService).list(1L, com.messaging.channel.enums.ChannelType.WHATSAPP);
    verify(channelService, never()).list(1L, com.messaging.channel.enums.ChannelType.EMAIL);
    verifyNoInteractions(contactService, broadcastService, emailOverview, adCenter);
  }

  @Test
  void failedContactReadLeavesOtherContactDataAvailable() {
    when(authorizationService.hasPermission(eq(2L), eq(1L), anyString()))
        .thenAnswer(invocation -> CONTACT_READ.equals(invocation.getArgument(2)));
    var summary =
        new com.messaging.contacts.core.api.ContactDtos.ContactDashboardSummary(
            15, 3, 5, 6, 7, java.util.List.of());
    when(contactService.dashboardSummary(1L)).thenReturn(summary);
    when(contactService.search(eq(1L), any())).thenThrow(new IllegalStateException("Unavailable"));
    var result = service.get(1L, 2L);
    assertThat(result.contacts().summary()).isEqualTo(summary);
    assertThat(result.contacts().recent()).isEmpty();
    assertThat(result.unavailableSections()).containsExactly("contacts.recent");
    verifyNoInteractions(broadcastService, inboxService, emailOverview, adCenter);
  }
}

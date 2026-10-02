package com.messaging.managedsms.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.dto.CreateBroadcastMessageRequest;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.repository.*;
import com.messaging.broadcast.service.*;
import com.messaging.channel.enums.ChannelType;
import com.messaging.contacts.audience.AudienceSourceResolver;
import com.messaging.contacts.audience.ContactAudienceSelectionService;
import com.messaging.emailtemplate.service.EmailTemplateService;
import com.messaging.managedsms.runtime.ManagedSmsTemplateService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManagedSmsComposeContractTests {

  @Test
  void managedSmsIgnoresScratchBodyAndPersistsRenderedApprovedTemplate() {
    BroadcastGroupService groupService = mock(BroadcastGroupService.class);
    BroadcastMessageRepository messages = mock(BroadcastMessageRepository.class);
    BroadcastRunRepository runs = mock(BroadcastRunRepository.class);
    BroadcastStatsRepository stats = mock(BroadcastStatsRepository.class);
    BroadcastRecipientRepository recipients = mock(BroadcastRecipientRepository.class);
    BroadcastSenderService senders = mock(BroadcastSenderService.class);
    BroadcastStateMachine stateMachine = mock(BroadcastStateMachine.class);
    BroadcastLaunchService launch = mock(BroadcastLaunchService.class);
    EmailTemplateService emailTemplates = mock(EmailTemplateService.class);
    ManagedSmsTemplateService smsTemplates = mock(ManagedSmsTemplateService.class);
    AudienceSourceResolver audienceSourceResolver = mock(AudienceSourceResolver.class);
    ContactAudienceSelectionService contactAudienceSelectionService =
        mock(ContactAudienceSelectionService.class);
    BroadcastAudienceFreezeService audienceFreezeService = mock(BroadcastAudienceFreezeService.class);
    BroadcastWhatsAppTemplateService whatsAppTemplateService =
        mock(BroadcastWhatsAppTemplateService.class);

    BroadcastGroup group = new BroadcastGroup();
    group.setId(100L);
    group.setCompanyId(10L);
    group.setChannelType(ChannelType.SMS);
    when(groupService.require(10L, 100L)).thenReturn(group);

    BroadcastSender sender = new BroadcastSender();
    sender.setId(20L);
    sender.setCompanyId(10L);
    sender.setConnectionId(30L);
    sender.setProvider(BroadcastSenderProvider.MSG91_SMS);
    when(senders.requireActive(10L, 20L)).thenReturn(sender);

    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(30L);
    connection.setCompanyId(10L);
    connection.setProvider(BroadcastSenderProvider.MSG91_SMS);
    connection.setManagementMode(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    when(senders.requireConnection(10L, 30L)).thenReturn(connection);

    when(smsTemplates.renderForDraft(10L, 20L, 500L, Map.of("order_id", "A123")))
        .thenReturn(
            new ManagedSmsTemplateService.DraftTemplate(
                500L, 3L, "Your order A123 has shipped", Map.of("order_id", "A123")));
    when(messages.save(any(BroadcastMessage.class)))
        .thenAnswer(
            invocation -> {
              BroadcastMessage message = invocation.getArgument(0);
              message.setId(700L);
              return message;
            });
    when(runs.findByBroadcastMessageId(700L)).thenReturn(Optional.empty());

    BroadcastMessageService service =
        new BroadcastMessageService(
            groupService,
            messages,
            runs,
            stats,
            recipients,
            senders,
            stateMachine,
            launch,
            emailTemplates,
            smsTemplates,
            audienceSourceResolver,
            contactAudienceSelectionService,
            audienceFreezeService,
            whatsAppTemplateService,
            new ObjectMapper());

    service.create(
        10L,
        11L,
        100L,
        new CreateBroadcastMessageRequest(
            null,
            500L,
            20L,
            null,
            "ATTACK: arbitrary scratch body",
            null,
            Map.of("order_id", "A123")));

    ArgumentCaptor<BroadcastMessage> saved = ArgumentCaptor.forClass(BroadcastMessage.class);
    verify(messages).save(saved.capture());
    assertThat(saved.getValue().getBodyText()).isEqualTo("Your order A123 has shipped");
    assertThat(saved.getValue().getBodyText()).doesNotContain("ATTACK");
    assertThat(saved.getValue().getSourceTemplateId()).isEqualTo(500L);
    assertThat(saved.getValue().getSourceTemplateVersion()).isEqualTo(3L);
  }
}

package com.messaging.inbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.meta.runtime.MetaWhatsAppInboxReadStateService;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.inbox.controller.InboxController;
import com.messaging.inbox.dto.InboxPageResponse;
import com.messaging.inbox.repository.InboxConversationRepository;
import com.messaging.inbox.repository.InboxMessageRepository;
import com.messaging.inbox.service.InboxAttachmentStreamingService;
import com.messaging.inbox.service.InboxQueryService;
import com.messaging.inbox.service.InboxReadStateQueueService;
import com.messaging.inbox.service.InboxReadStateService;
import com.messaging.security.service.CurrentUserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InboxControllerWhatsAppTests {

  @Test
  void whatsappConversationCannotFallBackToEmailReadPermission() {
    Fixture fixture = fixture();
    when(fixture.conversationRepository.findProviderTypeByIdAndCompanyId(100L, 10L))
        .thenReturn(Optional.of(ProviderType.META_WHATSAPP));
    when(fixture.authorization.hasPermission(7L, 10L, CompanyPermissionKeys.WHATSAPP_READ))
        .thenReturn(false);
    when(fixture.authorization.hasPermission(7L, 10L, CompanyPermissionKeys.EMAIL_READ))
        .thenReturn(true);

    assertThatThrownBy(() -> fixture.controller.conversation(10L, 100L))
        .isInstanceOf(NotFoundException.class);

    verify(fixture.authorization)
        .hasPermission(7L, 10L, CompanyPermissionKeys.WHATSAPP_READ);
    verify(fixture.authorization, never())
        .hasPermission(7L, 10L, CompanyPermissionKeys.EMAIL_READ);
    verify(fixture.queryService, never()).getConversation(any(), any());
  }

  @Test
  void conversationDetailAuthorizesWithoutLoadingLazyConnection() {
    Fixture fixture = fixture();
    when(fixture.conversationRepository.findProviderTypeByIdAndCompanyId(100L, 10L))
        .thenReturn(Optional.of(ProviderType.GMAIL));
    when(fixture.authorization.hasPermission(7L, 10L, CompanyPermissionKeys.EMAIL_READ))
        .thenReturn(true);

    fixture.controller.conversationDetail(10L, 100L);

    verify(fixture.conversationRepository).findProviderTypeByIdAndCompanyId(100L, 10L);
    verify(fixture.conversationRepository, never()).findByIdAndCompanyId(100L, 10L);
    verify(fixture.authorization).hasPermission(7L, 10L, CompanyPermissionKeys.EMAIL_READ);
    verify(fixture.queryService).getConversationDetail(10L, 100L);
  }

  @Test
  void mixedInboxPassesIndependentChannelVisibilityFlags() {
    Fixture fixture = fixture();
    when(fixture.authorization.hasPermission(7L, 10L, CompanyPermissionKeys.EMAIL_READ))
        .thenReturn(true);
    when(fixture.authorization.hasPermission(7L, 10L, CompanyPermissionKeys.WHATSAPP_READ))
        .thenReturn(false);
    when(fixture.queryService.listConversations(
            eq(10L),
            eq(null),
            eq(EmailFolder.INBOX),
            eq(0),
            eq(30),
            eq(true),
            eq(false)))
        .thenReturn(new InboxPageResponse<>(List.of(), 0, 30, 0, 0));

    fixture.controller.conversations(10L, null, EmailFolder.INBOX, 0, 30);

    verify(fixture.queryService)
        .listConversations(10L, null, EmailFolder.INBOX, 0, 30, true, false);
  }

  private Fixture fixture() {
    InboxQueryService queryService = mock(InboxQueryService.class);
    CompanyAuthorizationService authorization = mock(CompanyAuthorizationService.class);
    CurrentUserService currentUser = mock(CurrentUserService.class);
    when(currentUser.currentUserId()).thenReturn(7L);
    InboxConversationRepository conversationRepository =
        mock(InboxConversationRepository.class);
    InboxController controller =
        new InboxController(
            queryService,
            mock(InboxAttachmentStreamingService.class),
            mock(InboxReadStateService.class),
            mock(InboxReadStateQueueService.class),
            mock(MetaWhatsAppInboxReadStateService.class),
            authorization,
            currentUser,
            mock(ChannelConnectionRepository.class),
            conversationRepository,
            mock(InboxMessageRepository.class));
    return new Fixture(controller, queryService, authorization, conversationRepository);
  }

  private record Fixture(
      InboxController controller,
      InboxQueryService queryService,
      CompanyAuthorizationService authorization,
      InboxConversationRepository conversationRepository) {}
}

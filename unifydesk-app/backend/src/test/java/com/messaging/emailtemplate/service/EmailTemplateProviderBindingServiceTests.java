package com.messaging.emailtemplate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderConnectionStatus;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.msg91.Msg91EmailGateway;
import com.messaging.broadcast.msg91.Msg91EmailTemplateSnapshot;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.emailtemplate.entity.EmailTemplate;
import com.messaging.emailtemplate.entity.EmailTemplateProviderBinding;
import com.messaging.emailtemplate.entity.EmailTemplateVersion;
import com.messaging.emailtemplate.enums.EmailTemplateProviderBindingStatus;
import com.messaging.emailtemplate.enums.EmailTemplatePurpose;
import com.messaging.emailtemplate.repository.EmailTemplateProviderBindingRepository;
import com.messaging.emailtemplate.repository.EmailTemplateRepository;
import com.messaging.emailtemplate.repository.EmailTemplateVersionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailTemplateProviderBindingServiceTests {

  private static final Long COMPANY_ID = 10L;
  private static final Long TEMPLATE_ID = 20L;
  private static final Long VERSION_ID = 30L;
  private static final Long CONNECTION_ID = 40L;

  @Mock private EmailTemplateProviderBindingRepository bindingRepository;
  @Mock private EmailTemplateRepository templateRepository;
  @Mock private EmailTemplateVersionRepository versionRepository;
  @Mock private BroadcastSenderConnectionRepository connectionRepository;
  @Mock private EmailTemplateService emailTemplateService;
  @Mock private Msg91EmailGateway msg91EmailGateway;

  private EmailTemplateProviderBindingService service;

  @BeforeEach
  void setUp() {
    service =
        new EmailTemplateProviderBindingService(
            bindingRepository,
            templateRepository,
            versionRepository,
            connectionRepository,
            emailTemplateService,
            msg91EmailGateway);
  }

  @Test
  void rejectsMarketingTemplateBeforeCallingMsg91() {
    when(templateRepository.findByIdAndCompanyId(TEMPLATE_ID, COMPANY_ID))
        .thenReturn(Optional.of(template()));
    when(emailTemplateService.requirePublishedForBroadcast(COMPANY_ID, TEMPLATE_ID))
        .thenReturn(published(EmailTemplatePurpose.MARKETING));

    assertThatThrownBy(() -> service.syncMsg91(COMPANY_ID, TEMPLATE_ID, CONNECTION_ID))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("transactional");

    verify(msg91EmailGateway, never())
        .createTemplate(any(), any(), any(), any(), any());
  }

  @Test
  void syncsExactTransactionalVersionAndStoresProviderTemplate() {
    EmailTemplate template = template();
    BroadcastSenderConnection connection = connection();

    when(templateRepository.findByIdAndCompanyId(TEMPLATE_ID, COMPANY_ID))
        .thenReturn(Optional.of(template));
    when(emailTemplateService.requirePublishedForBroadcast(COMPANY_ID, TEMPLATE_ID))
        .thenReturn(published(EmailTemplatePurpose.TRANSACTIONAL));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection));
    when(
            bindingRepository.findByTemplateVersionIdAndConnectionIdAndProvider(
                VERSION_ID, CONNECTION_ID, BroadcastSenderProvider.MSG91_EMAIL))
        .thenReturn(Optional.empty());
    when(bindingRepository.saveAndFlush(any(EmailTemplateProviderBinding.class)))
        .thenAnswer(
            invocation -> {
              EmailTemplateProviderBinding binding = invocation.getArgument(0);
              binding.setId(50L);
              return binding;
            });
    when(bindingRepository.save(any(EmailTemplateProviderBinding.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(msg91EmailGateway.createTemplate(any(), any(), any(), any(), any()))
        .thenReturn(
            new Msg91EmailTemplateSnapshot(
                "provider-template",
                "messaging-20-v3-01234567",
                "active",
                EmailTemplateProviderBindingStatus.READY));

    var response = service.syncMsg91(COMPANY_ID, TEMPLATE_ID, CONNECTION_ID);

    assertThat(response.templateVersionId()).isEqualTo(VERSION_ID.toString());
    assertThat(response.templateVersion()).isEqualTo(3);
    assertThat(response.connectionId()).isEqualTo(CONNECTION_ID.toString());
    assertThat(response.providerTemplateId()).isEqualTo("provider-template");
    assertThat(response.status()).isEqualTo(EmailTemplateProviderBindingStatus.READY);
    assertThat(response.sendable()).isTrue();

    verify(msg91EmailGateway)
        .createTemplate(
            connection,
            "Messaging - Welcome - v3",
            "messaging-20-v3-01234567",
            "Welcome {{first_name}}",
            "<p>Hello {{first_name}}</p>");
  }

  @Test
  void retryOfExistingPendingBindingRefreshesInsteadOfCreatingDuplicate() {
    EmailTemplateProviderBinding binding = new EmailTemplateProviderBinding();
    binding.setId(50L);
    binding.setCompanyId(COMPANY_ID);
    binding.setTemplateId(TEMPLATE_ID);
    binding.setTemplateVersionId(VERSION_ID);
    binding.setTemplateVersionNumber(3);
    binding.setConnectionId(CONNECTION_ID);
    binding.setProvider(BroadcastSenderProvider.MSG91_EMAIL);
    binding.setProviderTemplateId("provider-template");
    binding.setProviderTemplateSlug("messaging-20-v3-01234567");
    binding.setStatus(EmailTemplateProviderBindingStatus.PENDING_REVIEW);
    binding.setContentHash(hash());

    when(templateRepository.findByIdAndCompanyId(TEMPLATE_ID, COMPANY_ID))
        .thenReturn(Optional.of(template()));
    when(emailTemplateService.requirePublishedForBroadcast(COMPANY_ID, TEMPLATE_ID))
        .thenReturn(published(EmailTemplatePurpose.TRANSACTIONAL));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection()));
    when(
            bindingRepository.findByTemplateVersionIdAndConnectionIdAndProvider(
                VERSION_ID, CONNECTION_ID, BroadcastSenderProvider.MSG91_EMAIL))
        .thenReturn(Optional.of(binding));
    when(
            msg91EmailGateway.findTemplate(
                any(BroadcastSenderConnection.class),
                eq("provider-template"),
                eq("messaging-20-v3-01234567")))
        .thenReturn(
            new Msg91EmailTemplateSnapshot(
                "provider-template",
                "messaging-20-v3-01234567",
                "pending",
                EmailTemplateProviderBindingStatus.PENDING_REVIEW));
    when(bindingRepository.save(any(EmailTemplateProviderBinding.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.syncMsg91(COMPANY_ID, TEMPLATE_ID, CONNECTION_ID);

    assertThat(response.status()).isEqualTo(EmailTemplateProviderBindingStatus.PENDING_REVIEW);
    verify(msg91EmailGateway)
        .findTemplate(
            any(BroadcastSenderConnection.class),
            eq("provider-template"),
            eq("messaging-20-v3-01234567"));
    verify(msg91EmailGateway, never())
        .createTemplate(any(), any(), any(), any(), any());
  }

  @Test
  void pendingBindingCannotLaunchBroadcast() {
    EmailTemplateVersion version = new EmailTemplateVersion();
    version.setId(VERSION_ID);
    version.setTemplateId(TEMPLATE_ID);
    version.setVersionNumber(3);
    version.setPurpose(EmailTemplatePurpose.TRANSACTIONAL);
    version.setContentHash(hash());

    EmailTemplateProviderBinding binding = new EmailTemplateProviderBinding();
    binding.setId(50L);
    binding.setCompanyId(COMPANY_ID);
    binding.setTemplateId(TEMPLATE_ID);
    binding.setTemplateVersionId(VERSION_ID);
    binding.setTemplateVersionNumber(3);
    binding.setConnectionId(CONNECTION_ID);
    binding.setProvider(BroadcastSenderProvider.MSG91_EMAIL);
    binding.setProviderTemplateSlug("messaging-20-v3-01234567");
    binding.setProviderTemplateId("provider-template");
    binding.setStatus(EmailTemplateProviderBindingStatus.PENDING_REVIEW);
    binding.setContentHash(hash());

    when(templateRepository.findByIdAndCompanyId(TEMPLATE_ID, COMPANY_ID))
        .thenReturn(Optional.of(template()));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection()));
    when(versionRepository.findByTemplateIdAndVersionNumber(TEMPLATE_ID, 3))
        .thenReturn(Optional.of(version));
    when(
            bindingRepository.findByTemplateVersionIdAndConnectionIdAndProvider(
                VERSION_ID, CONNECTION_ID, BroadcastSenderProvider.MSG91_EMAIL))
        .thenReturn(Optional.of(binding));

    assertThatThrownBy(
            () ->
                service.requireSendableForBroadcast(
                    COMPANY_ID, TEMPLATE_ID, 3, CONNECTION_ID))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("PENDING_REVIEW");
  }


  @Test
  void syncedButUnconfirmedBindingCannotLaunchBroadcast() {
    EmailTemplateVersion version = version();
    EmailTemplateProviderBinding binding = readyBinding();
    binding.setStatus(EmailTemplateProviderBindingStatus.SYNCED);

    stubSendableLookup(version, binding);

    assertThatThrownBy(
            () ->
                service.requireSendableForBroadcast(
                    COMPANY_ID, TEMPLATE_ID, 3, CONNECTION_ID))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("SYNCED");
  }

  @Test
  void staleContentHashCannotLaunchBroadcast() {
    EmailTemplateVersion version = version();
    EmailTemplateProviderBinding binding = readyBinding();
    binding.setContentHash("stale");

    stubSendableLookup(version, binding);

    assertThatThrownBy(
            () ->
                service.requireSendableForBroadcast(
                    COMPANY_ID, TEMPLATE_ID, 3, CONNECTION_ID))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("stale");
  }

  @Test
  void marketingVersionCannotLaunchThroughMsg91() {
    EmailTemplateVersion version = version();
    version.setPurpose(EmailTemplatePurpose.MARKETING);
    EmailTemplateProviderBinding binding = readyBinding();

    when(templateRepository.findByIdAndCompanyId(TEMPLATE_ID, COMPANY_ID))
        .thenReturn(Optional.of(template()));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection()));
    when(versionRepository.findByTemplateIdAndVersionNumber(TEMPLATE_ID, 3))
        .thenReturn(Optional.of(version));

    assertThatThrownBy(
            () ->
                service.requireSendableForBroadcast(
                    COMPANY_ID, TEMPLATE_ID, 3, CONNECTION_ID))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("transactional");
  }

  private EmailTemplateVersion version() {
    EmailTemplateVersion version = new EmailTemplateVersion();
    version.setId(VERSION_ID);
    version.setTemplateId(TEMPLATE_ID);
    version.setVersionNumber(3);
    version.setPurpose(EmailTemplatePurpose.TRANSACTIONAL);
    version.setContentHash(hash());
    return version;
  }

  private EmailTemplateProviderBinding readyBinding() {
    EmailTemplateProviderBinding binding = new EmailTemplateProviderBinding();
    binding.setId(50L);
    binding.setCompanyId(COMPANY_ID);
    binding.setTemplateId(TEMPLATE_ID);
    binding.setTemplateVersionId(VERSION_ID);
    binding.setTemplateVersionNumber(3);
    binding.setConnectionId(CONNECTION_ID);
    binding.setProvider(BroadcastSenderProvider.MSG91_EMAIL);
    binding.setProviderTemplateSlug("messaging-20-v3-01234567");
    binding.setProviderTemplateId("provider-template");
    binding.setStatus(EmailTemplateProviderBindingStatus.READY);
    binding.setContentHash(hash());
    return binding;
  }

  private void stubSendableLookup(
      EmailTemplateVersion version, EmailTemplateProviderBinding binding) {
    when(templateRepository.findByIdAndCompanyId(TEMPLATE_ID, COMPANY_ID))
        .thenReturn(Optional.of(template()));
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection()));
    when(versionRepository.findByTemplateIdAndVersionNumber(TEMPLATE_ID, 3))
        .thenReturn(Optional.of(version));
    when(
            bindingRepository.findByTemplateVersionIdAndConnectionIdAndProvider(
                VERSION_ID, CONNECTION_ID, BroadcastSenderProvider.MSG91_EMAIL))
        .thenReturn(Optional.of(binding));
  }

  private EmailTemplate template() {
    EmailTemplate template = new EmailTemplate();
    template.setId(TEMPLATE_ID);
    template.setCompanyId(COMPANY_ID);
    template.setName("Welcome");
    template.setPurpose(EmailTemplatePurpose.TRANSACTIONAL);
    return template;
  }

  private BroadcastSenderConnection connection() {
    BroadcastSenderConnection connection = new BroadcastSenderConnection();
    connection.setId(CONNECTION_ID);
    connection.setCompanyId(COMPANY_ID);
    connection.setProvider(BroadcastSenderProvider.MSG91_EMAIL);
    connection.setStatus(BroadcastSenderConnectionStatus.ACTIVE);
    connection.setSendingEnabled(true);
    return connection;
  }

  private EmailTemplateService.PublishedEmail published(EmailTemplatePurpose purpose) {
    return new EmailTemplateService.PublishedEmail(
        TEMPLATE_ID,
        VERSION_ID,
        3,
        purpose,
        "Welcome {{first_name}}",
        "Hello {{first_name}}",
        "<p>Hello {{first_name}}</p>",
        hash(),
        List.of("first_name"));
  }

  private String hash() {
    return "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  }
}

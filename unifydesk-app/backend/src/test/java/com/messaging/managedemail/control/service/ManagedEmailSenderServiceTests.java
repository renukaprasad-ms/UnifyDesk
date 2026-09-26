package com.messaging.managedemail.control.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.broadcast.repository.BroadcastSenderRepository;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.SenderIdentity;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.dto.ManagedEmailDtos.CreateSenderRequest;
import com.messaging.managedemail.control.entity.ManagedEmailDomain;
import com.messaging.managedemail.control.entity.ManagedEmailSenderBinding;
import com.messaging.managedemail.control.entity.ManagedEmailTenant;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.DomainStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.DomainType;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.SendingStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.TenantStatus;
import com.messaging.managedemail.control.repository.ManagedEmailDomainRepository;
import com.messaging.managedemail.control.repository.ManagedEmailSenderBindingRepository;
import com.messaging.managedemail.control.repository.ManagedEmailTenantRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ManagedEmailSenderServiceTests {

  @Mock private ManagedEmailDomainRepository domainRepository;
  @Mock private ManagedEmailTenantRepository tenantRepository;
  @Mock private ManagedEmailSenderBindingRepository bindingRepository;
  @Mock private BroadcastSenderConnectionRepository connectionRepository;
  @Mock private BroadcastSenderRepository senderRepository;
  @Mock private ChannelConnectionRepository channelConnectionRepository;
  @Mock private SenderIdentityRepository senderIdentityRepository;
  @Mock private ManagedEmailEntitlementService entitlementService;
  @Mock private ManagedEmailSenderLifecycleService lifecycleService;
  @Mock private JdbcTemplate jdbcTemplate;

  private ManagedEmailSenderService service;

  @BeforeEach
  void setUp() {
    ManagedEmailProperties properties = new ManagedEmailProperties();
    properties.setEnabled(true);
    properties.setPlatformSendingEnabled(true);
    properties.setAwsRegion("ap-south-1");
    service =
        new ManagedEmailSenderService(
            domainRepository,
            tenantRepository,
            bindingRepository,
            connectionRepository,
            senderRepository,
            channelConnectionRepository,
            senderIdentityRepository,
            entitlementService,
            lifecycleService,
            properties,
            jdbcTemplate);
  }

  @Test
  void rejectsForeignDomainIdWithoutFallingBackToUnscopedLookup() {
    when(domainRepository.findByIdAndCompanyId(22L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.create(
                    1L, 7L, new CreateSenderRequest("22", "offers", "Offers", null)))
        .isInstanceOf(NotFoundException.class);

    verify(senderRepository, never()).save(any(BroadcastSender.class));
  }

  @Test
  void rejectsFullAddressInjectionInLocalPart() {
    ManagedEmailDomain domain = activeDomain();
    ManagedEmailTenant tenant = activeTenant();
    when(domainRepository.findByIdAndCompanyId(22L, 1L)).thenReturn(Optional.of(domain));
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));

    assertThatThrownBy(
            () ->
                service.create(
                    1L,
                    7L,
                    new CreateSenderRequest("22", "offers@evil.example", "Offers", null)))
        .isInstanceOf(BadRequestException.class);

    verify(senderRepository, never()).save(any(BroadcastSender.class));
  }

  @Test
  void createsManagedBroadcastSenderWithoutProviderCredentials() {
    ManagedEmailDomain domain = activeDomain();
    ManagedEmailTenant tenant = activeTenant();
    when(domainRepository.findByIdAndCompanyId(22L, 1L)).thenReturn(Optional.of(domain));
    when(tenantRepository.findByIdAndCompanyId(33L, 1L)).thenReturn(Optional.of(tenant));
    when(senderRepository.findByCompanyIdAndProviderAndFromAddressIgnoreCase(
            1L, BroadcastSenderProvider.AMAZON_SES, "offers@example.com"))
        .thenReturn(Optional.empty());
    when(connectionRepository
            .findFirstByCompanyIdAndProviderAndManagementModeAndStatusNot(
                eq(1L),
                eq(BroadcastSenderProvider.AMAZON_SES),
                eq(BroadcastSenderManagementMode.MESSAGING_MANAGED),
                any()))
        .thenReturn(Optional.empty());
    when(jdbcTemplate.queryForObject(any(String.class), eq(String.class), anyLong()))
        .thenReturn("");

    AtomicReference<ChannelConnection> channel = new AtomicReference<>();
    when(channelConnectionRepository.save(any(ChannelConnection.class)))
        .thenAnswer(
            invocation -> {
              ChannelConnection saved = invocation.getArgument(0);
              channel.set(saved);
              return saved;
            });
    when(connectionRepository.save(any(BroadcastSenderConnection.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(senderRepository.save(any(BroadcastSender.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(channelConnectionRepository.findByIdAndCompanyId(anyLong(), eq(1L)))
        .thenAnswer(invocation -> Optional.ofNullable(channel.get()));
    when(senderIdentityRepository.findFirstByConnectionIdAndDefaultIdentityTrue(anyLong()))
        .thenReturn(Optional.empty());
    when(senderIdentityRepository.save(any(SenderIdentity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(bindingRepository.save(any(ManagedEmailSenderBinding.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.create(
            1L,
            7L,
            new CreateSenderRequest("22", "offers", "Example Offers", "reply@example.com"));

    assertThat(response.fromAddress()).isEqualTo("offers@example.com");
    assertThat(response.managementMode()).isEqualTo(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    assertThat(response.connectionName()).isEqualTo("UnifyDesk Managed Email");
    assertThat(response.region()).isEqualTo("MANAGED");

    var connectionCaptor =
        org.mockito.ArgumentCaptor.forClass(BroadcastSenderConnection.class);
    verify(connectionRepository).save(connectionCaptor.capture());
    BroadcastSenderConnection connection = connectionCaptor.getValue();
    assertThat(connection.getEncryptedCredentials()).isNull();
    assertThat(connection.getRoleArn()).isNull();
    assertThat(connection.getProvider()).isEqualTo(BroadcastSenderProvider.AMAZON_SES);
  }

  private ManagedEmailDomain activeDomain() {
    ManagedEmailDomain domain = new ManagedEmailDomain();
    domain.setId(22L);
    domain.setCompanyId(1L);
    domain.setTenantId(33L);
    domain.setDomainType(DomainType.CUSTOMER_DOMAIN);
    domain.setNormalizedDomain("example.com");
    domain.setDisplayDomain("example.com");
    domain.setStatus(DomainStatus.ACTIVE);
    return domain;
  }

  private ManagedEmailTenant activeTenant() {
    ManagedEmailTenant tenant = new ManagedEmailTenant();
    tenant.setId(33L);
    tenant.setCompanyId(1L);
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setSendingStatus(SendingStatus.ENABLED);
    tenant.setLastProviderSyncAt(Instant.now());
    return tenant;
  }
}

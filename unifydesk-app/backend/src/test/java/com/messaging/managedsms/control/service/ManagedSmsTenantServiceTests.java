package com.messaging.managedsms.control.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.entity.ManagedSmsTenant;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.ProviderMode;
import com.messaging.managedsms.control.provisioning.ManagedSmsProvisioningService;
import com.messaging.managedsms.control.repository.ManagedSmsTenantRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ManagedSmsTenantServiceTests {

  @Mock private ManagedSmsTenantRepository tenantRepository;
  @Mock private BroadcastSenderConnectionRepository connectionRepository;
  @Mock private ChannelConnectionRepository channelConnectionRepository;
  @Mock private ManagedSmsProvisioningService provisioningService;
  @Mock private JdbcTemplate jdbcTemplate;

  @Test
  void createsManagedConnectionWithoutCustomerProviderCredentials() {
    ManagedSmsProperties properties = new ManagedSmsProperties();
    properties.setProviderMode(ProviderMode.CHILD_PER_COMPANY);
    when(jdbcTemplate.queryForObject(anyString(), eq(String.class), anyLong())).thenReturn("");
    when(tenantRepository.findByCompanyId(1L)).thenReturn(Optional.empty());
    when(connectionRepository.findFirstByCompanyIdAndProviderAndManagementModeAndStatusNot(
            1L,
            BroadcastSenderProvider.MSG91_SMS,
            BroadcastSenderManagementMode.MESSAGING_MANAGED,
            BroadcastSenderConnectionStatus.DISABLED))
        .thenReturn(Optional.empty());
    when(channelConnectionRepository.save(any(ChannelConnection.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(connectionRepository.save(any(BroadcastSenderConnection.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(tenantRepository.save(any(ManagedSmsTenant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ManagedSmsTenantService service =
        new ManagedSmsTenantService(
            tenantRepository,
            connectionRepository,
            channelConnectionRepository,
            provisioningService,
            properties,
            jdbcTemplate);

    service.ensureTenant(1L, 7L);

    ArgumentCaptor<BroadcastSenderConnection> captor =
        ArgumentCaptor.forClass(BroadcastSenderConnection.class);
    verify(connectionRepository).save(captor.capture());
    BroadcastSenderConnection connection = captor.getValue();
    assertThat(connection.getProvider()).isEqualTo(BroadcastSenderProvider.MSG91_SMS);
    assertThat(connection.getManagementMode())
        .isEqualTo(BroadcastSenderManagementMode.MESSAGING_MANAGED);
    assertThat(connection.getEncryptedCredentials()).isNull();
    assertThat(connection.getProviderConfigJson()).isNull();
    assertThat(connection.getRoleArn()).isNull();
    assertThat(connection.isSendingEnabled()).isFalse();
  }
}

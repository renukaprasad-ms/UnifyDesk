package com.messaging.channel.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.DisconnectDataPolicy;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.provider.ChannelProviderRegistry;
import com.messaging.channel.provider.ProviderAccountHealth;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.common.exception.BadRequestException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailChannelOperationsServiceTests {

  private static final Long COMPANY_ID = 11L;
  private static final Long CONNECTION_ID = 22L;
  private static final Long USER_ID = 33L;

  private ChannelConnectionRepository connectionRepository;
  private ChannelConnectionService channelConnectionService;
  private TestEmailProvider provider;
  private EmailChannelOperationsService service;

  @BeforeEach
  void setUp() {
    connectionRepository = mock(ChannelConnectionRepository.class);
    channelConnectionService = mock(ChannelConnectionService.class);
    provider = new TestEmailProvider();
    service =
        new EmailChannelOperationsService(
            connectionRepository,
            new ChannelProviderRegistry(List.of(provider)),
            channelConnectionService,
            new EmailProviderMetrics(new SimpleMeterRegistry()));
  }

  @Test
  void dispatchesSendToProviderForActiveEmailConnection() {
    ChannelConnection connection = connection(ChannelType.EMAIL, ChannelConnectionStatus.ACTIVE);
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection));

    EmailSendCommand command =
        new EmailSendCommand(
            List.of("to@example.com"),
            List.of(),
            List.of(),
            "subject",
            "body",
            null,
            null,
            false,
            null,
            null,
            List.of());

    EmailSendResult result = service.send(COMPANY_ID, CONNECTION_ID, command);

    assertThat(provider.lastSendConnection).isSameAs(connection);
    assertThat(provider.lastSendCommand).isSameAs(command);
    assertThat(result.externalMessageId()).isEqualTo("external-1");
  }

  @Test
  void rejectsSendForInactiveEmailConnection() {
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(
            Optional.of(connection(ChannelType.EMAIL, ChannelConnectionStatus.DISCONNECTED)));

    assertThatThrownBy(
            () ->
                service.send(
                    COMPANY_ID,
                    CONNECTION_ID,
                    new EmailSendCommand(
                        List.of("to@example.com"),
                        List.of(),
                        List.of(),
                        null,
                        "body",
                        null,
                        null,
                        false,
                        null,
                        null,
                        List.of())))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("not active");
  }

  @Test
  void rejectsOperationsForNonEmailConnection() {
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(
            Optional.of(connection(ChannelType.WHATSAPP, ChannelConnectionStatus.ACTIVE)));

    assertThatThrownBy(() -> service.activeSync(COMPANY_ID, CONNECTION_ID))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("not an email channel");
  }

  @Test
  void delegatesDisconnectToGenericChannelLifecycle() {
    ChannelConnection connection = connection(ChannelType.EMAIL, ChannelConnectionStatus.ACTIVE);
    when(connectionRepository.findByIdAndCompanyId(CONNECTION_ID, COMPANY_ID))
        .thenReturn(Optional.of(connection));
    ChannelConnectionResponse expected =
        new ChannelConnectionResponse(
            CONNECTION_ID.toString(),
            COMPANY_ID.toString(),
            ChannelType.EMAIL,
            ProviderType.GMAIL,
            "Gmail",
            "owner@example.com",
            ChannelConnectionStatus.DISCONNECTED,
            USER_ID.toString(),
            null,
            Instant.now(),
            Instant.now().plusSeconds(60),
            Instant.now(),
            Instant.now(),
            true,
            ProviderType.GMAIL.capabilities());
    when(
            channelConnectionService.disconnect(
                COMPANY_ID,
                CONNECTION_ID,
                USER_ID,
                DisconnectDataPolicy.KEEP_7_DAYS))
        .thenReturn(expected);

    ChannelConnectionResponse response =
        service.disconnect(
            COMPANY_ID, CONNECTION_ID, USER_ID, DisconnectDataPolicy.KEEP_7_DAYS);

    assertThat(response).isSameAs(expected);
    verify(channelConnectionService)
        .disconnect(
            COMPANY_ID,
            CONNECTION_ID,
            USER_ID,
            DisconnectDataPolicy.KEEP_7_DAYS);
  }

  private ChannelConnection connection(
      ChannelType channelType, ChannelConnectionStatus status) {
    ChannelConnection connection = new ChannelConnection();
    connection.setId(CONNECTION_ID);
    connection.setCompanyId(COMPANY_ID);
    connection.setChannelType(channelType);
    connection.setProviderType(ProviderType.GMAIL);
    connection.setStatus(status);
    return connection;
  }

  private static final class TestEmailProvider
      implements EmailSendProvider, EmailSyncProvider {

    private ChannelConnection lastSendConnection;
    private EmailSendCommand lastSendCommand;

    @Override
    public ProviderType providerType() {
      return ProviderType.GMAIL;
    }

    @Override
    public EmailSendResult send(
        ChannelConnection connection, EmailSendCommand command) {
      lastSendConnection = connection;
      lastSendCommand = command;
      return new EmailSendResult(
          connection.getId().toString(),
          "external-1",
          null,
          "from@example.com",
          command.to(),
          command.cc(),
          command.bcc(),
          command.subject(),
          Instant.now());
    }

    @Override
    public EmailSyncQueuedResult queueSync(
        ChannelConnection connection, Long userId, EmailSyncRequest request) {
      return new EmailSyncQueuedResult(
          connection.getId().toString(),
          "job-1",
          "QUEUED",
          request.mode(),
          request.folder(),
          request.fromDate(),
          request.toDate(),
          request.messageCount());
    }

    @Override
    public AsyncJobResponse activeSync(ChannelConnection connection) {
      return null;
    }

    @Override
    public ProviderAccountHealth health(ChannelConnection connection) {
      return new ProviderAccountHealth(true, "ACTIVE", null, Instant.now());
    }
  }
}

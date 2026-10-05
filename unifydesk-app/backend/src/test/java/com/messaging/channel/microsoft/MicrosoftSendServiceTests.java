package com.messaging.channel.microsoft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.channel.email.EmailAttachmentService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.microsoft.dto.MicrosoftSendRequest;
import com.messaging.channel.microsoft.dto.MicrosoftSendResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MicrosoftSendServiceTests {

  @Mock private MicrosoftTokenService tokenService;
  @Mock private MicrosoftApiClient apiClient;
  @Mock private MicrosoftSyncStateService syncStateService;
  @Mock private EmailAttachmentService attachmentService;

  private MicrosoftSendService service;
  private ChannelConnection connection;

  @BeforeEach
  void setUp() {
    service = new MicrosoftSendService(tokenService, apiClient, syncStateService, attachmentService);
    connection = new ChannelConnection();
    connection.setId(10L);
    connection.setCompanyId(20L);
    connection.setExternalAccountId("owner@example.com");
    when(tokenService.accessToken(connection)).thenReturn("access-token");
    when(attachmentService.resolve(20L, List.of())).thenReturn(List.of());
  }

  @Test
  void replyDoesNotRequireExplicitRecipients() {
    MicrosoftSendRequest request =
        new MicrosoftSendRequest(
            List.of(), List.of(), List.of(), null, "Thanks", "message-1", false, List.of());

    MicrosoftSendResponse response = service.send(connection, request);

    verify(apiClient).reply("access-token", "message-1", "Thanks", false);
    verify(syncStateService).triggerConnection(10L);
    assertThat(response.from()).isEqualTo("owner@example.com");
    assertThat(response.to()).isEmpty();
  }
}

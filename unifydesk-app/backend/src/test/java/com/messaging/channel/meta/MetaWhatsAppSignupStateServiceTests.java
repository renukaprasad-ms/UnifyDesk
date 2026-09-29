package com.messaging.channel.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MetaWhatsAppSignupStateServiceTests {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private MetaWhatsAppSignupStateService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
    properties.setSignupSessionTtl(Duration.ofMinutes(10));
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    service =
        new MetaWhatsAppSignupStateService(
            redisTemplate, new ObjectMapper().findAndRegisterModules(), properties);
  }

  @Test
  void createsFreshHighEntropyStateOnlyOnExplicitCall() {
    MetaWhatsAppSignupStateService.SignupSession first = service.create(10L, 20L);
    MetaWhatsAppSignupStateService.SignupSession second = service.create(10L, 20L);

    assertThat(first.state()).isNotEqualTo(second.state());
    assertThat(first.state()).hasSizeGreaterThanOrEqualTo(43);
    assertThat(first.expiresAt()).isAfter(Instant.now());
  }

  @Test
  void consumeIsOneTimeAndReplayFails() {
    String raw =
        """
        {"companyId":10,"userId":20,"createdAt":"2026-09-29T05:00:00Z","expiresAt":"2099-09-29T05:10:00Z","mode":"CONNECT","expectedConnectionId":null,"expectedPhoneNumberId":null}
        """;
    when(valueOperations.getAndDelete(anyString())).thenReturn(raw, null);

    var consumed = service.consume("state", 10L, 20L);
    assertThat(consumed.mode()).isEqualTo(MetaWhatsAppSignupStateService.SignupMode.CONNECT);

    assertThatThrownBy(() -> service.consume("state", 10L, 20L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("already used");
  }

  @Test
  void mismatchedCompanyOrUserFailsAfterAtomicConsume() {
    String raw =
        """
        {"companyId":10,"userId":20,"createdAt":"2026-09-29T05:00:00Z","expiresAt":"2099-09-29T05:10:00Z","mode":"CONNECT","expectedConnectionId":null,"expectedPhoneNumberId":null}
        """;
    when(valueOperations.getAndDelete(anyString())).thenReturn(raw);

    assertThatThrownBy(() -> service.consume("state", 11L, 20L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("does not belong");
  }

  @Test
  void expiredStateFailsEvenIfRedisPayloadExists() {
    String raw =
        """
        {"companyId":10,"userId":20,"createdAt":"2020-01-01T00:00:00Z","expiresAt":"2020-01-01T00:10:00Z","mode":"CONNECT","expectedConnectionId":null,"expectedPhoneNumberId":null}
        """;
    when(valueOperations.getAndDelete(anyString())).thenReturn(raw);

    assertThatThrownBy(() -> service.consume("state", 10L, 20L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("expired");
  }
}

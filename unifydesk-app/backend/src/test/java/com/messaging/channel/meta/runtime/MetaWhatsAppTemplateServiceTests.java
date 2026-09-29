package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppSendRequest;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MetaWhatsAppTemplateServiceTests {

  @Test
  void approvedFreshTemplateAcceptsExactVariables() {
    MetaWhatsAppTemplateService service =
        service(template("APPROVED", Instant.now(), componentsWithTwoBodyVariables()));

    var prepared =
        service.prepare(
            10L,
            20L,
            "30",
            new MetaWhatsAppSendRequest.Parameters(
                List.of(), List.of("one", "two"), List.of()));

    assertThat(prepared.name()).isEqualTo("order_update");
    assertThat(prepared.providerComponents()).hasSize(1);
  }

  @Test
  void staleOrRejectedTemplateFailsClosed() {
    MetaWhatsAppTemplateService stale =
        service(
            template(
                "APPROVED",
                Instant.now().minus(Duration.ofHours(1)),
                componentsWithTwoBodyVariables()));
    MetaWhatsAppTemplateService rejected =
        service(template("REJECTED", Instant.now(), componentsWithTwoBodyVariables()));

    assertThatThrownBy(
            () ->
                stale.prepare(
                    10L,
                    20L,
                    "30",
                    new MetaWhatsAppSendRequest.Parameters(
                        List.of(), List.of("one", "two"), List.of())))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("WHATSAPP_TEMPLATE_NOT_APPROVED");
    assertThatThrownBy(
            () ->
                rejected.prepare(
                    10L,
                    20L,
                    "30",
                    new MetaWhatsAppSendRequest.Parameters(
                        List.of(), List.of("one", "two"), List.of())))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void foreignOrMissingTemplateIsHiddenAsNotFound() {
    MetaWhatsAppTemplateService service = service(null);

    assertThatThrownBy(
            () ->
                service.prepare(
                    10L,
                    20L,
                    "999",
                    new MetaWhatsAppSendRequest.Parameters(List.of(), List.of(), List.of())))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void wrongVariableCountIsRejected() {
    MetaWhatsAppTemplateService service =
        service(template("APPROVED", Instant.now(), componentsWithTwoBodyVariables()));

    assertThatThrownBy(
            () ->
                service.prepare(
                    10L,
                    20L,
                    "30",
                    new MetaWhatsAppSendRequest.Parameters(
                        List.of(), List.of("only-one"), List.of())))
        .isInstanceOf(com.messaging.common.exception.BadRequestException.class)
        .hasMessageContaining("WHATSAPP_TEMPLATE_PARAMETERS_INVALID");
  }

  private MetaWhatsAppTemplateService service(TemplateRow row) {
    ChannelConnection connection = new ChannelConnection();
    connection.setId(20L);
    connection.setCompanyId(10L);
    connection.setProviderType(ProviderType.META_WHATSAPP);
    ChannelConnectionRepository connectionRepository = mock(ChannelConnectionRepository.class);
    when(connectionRepository.findByIdAndCompanyId(20L, 10L))
        .thenReturn(Optional.of(connection));

    MetaWhatsAppRuntimeProperties properties = new MetaWhatsAppRuntimeProperties();
    properties.setTemplateMaxAge(Duration.ofMinutes(15));
    return new MetaWhatsAppTemplateService(
        new TemplateJdbcTemplate(row),
        new ObjectMapper(),
        connectionRepository,
        mock(MetaWhatsAppCredentialResolver.class),
        mock(MetaWhatsAppRuntimeGraphClient.class),
        properties);
  }

  private TemplateRow template(String status, Instant syncedAt, String components) {
    return new TemplateRow(30L, "order_update", "en_US", status, components, syncedAt);
  }

  private String componentsWithTwoBodyVariables() {
    return """
        [
          {
            "type":"BODY",
            "text":"Order {{1}} is now {{2}}"
          }
        ]
        """;
  }

  private record TemplateRow(
      long id,
      String name,
      String language,
      String status,
      String components,
      Instant syncedAt) {}

  private static final class TemplateJdbcTemplate extends JdbcTemplate {
    private final TemplateRow row;

    private TemplateJdbcTemplate(TemplateRow row) {
      this.row = row;
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      if (row == null) return List.of();
      try {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(row.id());
        when(rs.getString("name")).thenReturn(row.name());
        when(rs.getString("language")).thenReturn(row.language());
        when(rs.getString("status")).thenReturn(row.status());
        when(rs.getString("components_json")).thenReturn(row.components());
        when(rs.getTimestamp("synced_at")).thenReturn(Timestamp.from(row.syncedAt()));
        return List.of(rowMapper.mapRow(rs, 0));
      } catch (java.sql.SQLException exception) {
        throw new IllegalStateException(exception);
      }
    }
  }
}

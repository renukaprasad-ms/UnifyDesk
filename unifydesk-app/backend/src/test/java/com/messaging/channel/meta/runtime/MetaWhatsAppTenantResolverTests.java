package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.SenderIdentityStatus;
import com.messaging.common.exception.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MetaWhatsAppTenantResolverTests {

  @Test
  void unknownPhoneIsRejected() {
    MetaWhatsAppTenantResolver resolver =
        new MetaWhatsAppTenantResolver(
            new FixedJdbcTemplate(List.of()),
            mock(MetaWhatsAppControlPlaneProjectionRepository.class));

    assertThatThrownBy(() -> resolver.resolveTrustedPhone("unknown-phone"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void crossCompanyControlPlaneMismatchIsRejected() {
    var tenant =
        new MetaWhatsAppTenantResolver.ResolvedTenant(
            10L,
            20L,
            30L,
            ChannelConnectionStatus.ACTIVE,
            SenderIdentityStatus.ACTIVE,
            "phone-1");
    MetaWhatsAppControlPlaneProjectionRepository controlPlane =
        mock(MetaWhatsAppControlPlaneProjectionRepository.class);
    when(controlPlane.findByPhoneNumberId("phone-1"))
        .thenReturn(
            Optional.of(
                new MetaWhatsAppControlPlaneProjectionRepository.Projection(
                    20L,
                    999L,
                    "waba",
                    "waba",
                    "waba",
                    "phone-1",
                    "ACTIVE",
                    "VALID",
                    "REGISTERED",
                    "SUBSCRIBED",
                    "DISABLED",
                    null)));

    MetaWhatsAppTenantResolver resolver =
        new MetaWhatsAppTenantResolver(
            new FixedJdbcTemplate(List.of(tenant)), controlPlane);

    assertThatThrownBy(() -> resolver.resolveTrustedPhone("phone-1"))
        .isInstanceOf(MetaWhatsAppRuntimeException.class)
        .satisfies(
            exception ->
                org.assertj.core.api.Assertions.assertThat(
                        ((MetaWhatsAppRuntimeException) exception).safeCode())
                    .isEqualTo("WHATSAPP_TENANT_MAPPING_CONFLICT"));
  }

  private static final class FixedJdbcTemplate extends JdbcTemplate {
    private final List<?> rows;

    private FixedJdbcTemplate(List<?> rows) {
      this.rows = rows;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      return (List<T>) rows;
    }
  }
}

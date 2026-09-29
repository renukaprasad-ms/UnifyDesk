package com.messaging.channel.meta.runtime;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.enums.SenderIdentityStatus;
import com.messaging.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetaWhatsAppTenantResolver {
  private final JdbcTemplate jdbcTemplate;
  private final MetaWhatsAppControlPlaneProjectionRepository controlPlane;

  public ResolvedTenant resolveTrustedPhone(String phoneNumberId) {
    List<ResolvedTenant> rows =
        jdbcTemplate.query(
            """
            select c.id as connection_id, c.company_id, c.status as connection_status,
                   s.id as sender_id, s.status as sender_status
            from sender_identities s
            join channel_connections c on c.id = s.connection_id
            where s.external_id = ?
              and c.provider_type = 'META_WHATSAPP'
              and c.channel_type = 'WHATSAPP'
              and c.status <> 'DISCONNECTED'
              and s.status <> 'DISABLED'
            """,
            (rs, rowNum) ->
                new ResolvedTenant(
                    rs.getLong("company_id"),
                    rs.getLong("connection_id"),
                    rs.getLong("sender_id"),
                    ChannelConnectionStatus.valueOf(rs.getString("connection_status")),
                    SenderIdentityStatus.valueOf(rs.getString("sender_status")),
                    phoneNumberId),
            phoneNumberId);

    if (rows.isEmpty()) throw new NotFoundException("WHATSAPP_UNKNOWN_PHONE");
    if (rows.size() != 1) {
      throw new MetaWhatsAppRuntimeException(
          "WHATSAPP_TENANT_MAPPING_CONFLICT", false, "Ambiguous WhatsApp tenant mapping");
    }

    ResolvedTenant tenant = rows.getFirst();
    var projection = controlPlane.findByPhoneNumberId(phoneNumberId).orElse(null);
    if (projection != null
        && (projection.connectionId() != tenant.connectionId()
            || projection.companyId() != tenant.companyId())) {
      throw new MetaWhatsAppRuntimeException(
          "WHATSAPP_TENANT_MAPPING_CONFLICT", false, "Control-plane mapping mismatch");
    }
    return tenant;
  }

  public boolean isWhatsAppConnection(ChannelConnection connection) {
    return connection != null && connection.getProviderType() == ProviderType.META_WHATSAPP;
  }

  public record ResolvedTenant(
      long companyId,
      long connectionId,
      long senderIdentityId,
      ChannelConnectionStatus connectionStatus,
      SenderIdentityStatus senderStatus,
      String phoneNumberId) {}
}

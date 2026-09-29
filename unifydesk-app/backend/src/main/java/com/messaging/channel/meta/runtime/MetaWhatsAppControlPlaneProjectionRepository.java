package com.messaging.channel.meta.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MetaWhatsAppControlPlaneProjectionRepository {
  private final JdbcTemplate jdbcTemplate;

  public Optional<Projection> findByConnectionId(long connectionId) {
    return query("where connection_id = ?", connectionId).stream().findFirst();
  }

  public Optional<Projection> findByPhoneNumberId(String phoneNumberId) {
    return query("where selected_phone_number_id = ? and product_state <> 'DISCONNECTED'", phoneNumberId)
        .stream()
        .findFirst();
  }

  private List<Projection> query(String predicate, Object value) {
    return jdbcTemplate.query(
        """
        select connection_id, company_id, legacy_waba_id, whatsapp_account_id,
               messaging_account_id, selected_phone_number_id, product_state, auth_state,
               registration_state, subscription_state, coexistence_state, last_validated_at
        from meta_whatsapp_connections
        """ + predicate,
        (rs, rowNum) ->
            new Projection(
                rs.getLong("connection_id"),
                rs.getLong("company_id"),
                rs.getString("legacy_waba_id"),
                rs.getString("whatsapp_account_id"),
                rs.getString("messaging_account_id"),
                rs.getString("selected_phone_number_id"),
                rs.getString("product_state"),
                rs.getString("auth_state"),
                rs.getString("registration_state"),
                rs.getString("subscription_state"),
                rs.getString("coexistence_state"),
                rs.getTimestamp("last_validated_at") == null
                    ? null
                    : rs.getTimestamp("last_validated_at").toInstant()),
        value);
  }

  public record Projection(
      long connectionId,
      long companyId,
      String legacyWabaId,
      String whatsappAccountId,
      String messagingAccountId,
      String selectedPhoneNumberId,
      String productState,
      String authState,
      String registrationState,
      String subscriptionState,
      String coexistenceState,
      Instant lastValidatedAt) {
    public boolean usableForSend() {
      boolean productUsable = "ACTIVE".equals(productState) || "DEGRADED".equals(productState);
      boolean authUsable = "VALID".equals(authState) || "EXPIRING".equals(authState);
      return productUsable
          && authUsable
          && "REGISTERED".equals(registrationState)
          && "SUBSCRIBED".equals(subscriptionState);
    }
  }
}

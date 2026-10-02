package com.messaging.contacts.integration;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BroadcastRecipientContactProvenanceRepository {

  private final JdbcTemplate jdbcTemplate;

  public Optional<Provenance> find(long companyId, long recipientId) {
    return jdbcTemplate
        .query(
            """
            select contact_id, contact_identity_id, normalized_destination
            from broadcast_recipients
            where company_id = ? and id = ?
            """,
            (rs, rowNum) ->
                new Provenance(
                    (Long) rs.getObject("contact_id"),
                    (Long) rs.getObject("contact_identity_id"),
                    rs.getString("normalized_destination")),
            companyId,
            recipientId)
        .stream()
        .findFirst();
  }

  public record Provenance(Long contactId, Long contactIdentityId, String normalizedDestination) {}
}

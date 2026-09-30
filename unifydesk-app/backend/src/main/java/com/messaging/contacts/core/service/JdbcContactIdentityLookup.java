package com.messaging.contacts.core.service;

import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcContactIdentityLookup implements ContactIdentityLookup {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public Optional<ResolvedIdentity> findExact(
      long companyId, IdentityType type, String namespace, String normalizedValue) {
    return jdbcTemplate
        .query(
            """
            select contact_id, id, normalized_value
            from contact_identities
            where company_id = ?
              and identity_type = ?
              and coalesce(namespace, '') = coalesce(?, '')
              and normalized_value = ?
              and status = 'ACTIVE'
            limit 1
            """,
            (rs, rowNum) ->
                new ResolvedIdentity(
                    rs.getLong("contact_id"),
                    rs.getLong("id"),
                    rs.getString("normalized_value")),
            companyId,
            type.name(),
            namespace,
            normalizedValue)
        .stream()
        .findFirst();
  }
}

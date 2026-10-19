package com.messaging.contacts.integration;

import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.ContactChannelIdentity;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcContactCoreReadAdapter implements ContactCoreReadPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public boolean activeContactExists(long companyId, long contactId) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            """
            select exists (
              select 1
              from contacts c
              where c.company_id = :companyId
                and c.id = :contactId
                and c.status = 'ACTIVE'
            )
            """,
            Map.of("companyId", companyId, "contactId", contactId),
            Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  @Override
  public Optional<ContactChannelIdentity> loadIdentity(
      long companyId, long contactId, Long identityId, Channel channel) {
    Map<String, Object> params = new HashMap<>();
    params.put("companyId", companyId);
    params.put("contactId", contactId);
    params.put("identityType", identityType(channel));
    params.put("identityId", identityId);
    String identityPredicate = identityId == null ? "" : " and ci.id = :identityId ";
    return jdbcTemplate
        .query(
            """
            select c.id as contact_id, i.id as identity_id, i.normalized_value, c.display_name
            from contacts c
            join lateral (
              select ci.id, ci.normalized_value
              from contact_identities ci
              where ci.company_id = c.company_id
                and ci.contact_id = c.id
                and ci.status = 'ACTIVE'
                and ci.identity_type = :identityType
                %s
              order by ci.is_primary desc, ci.created_at asc, ci.id asc
              limit 1
            ) i on true
            where c.company_id = :companyId
              and c.id = :contactId
              and c.status = 'ACTIVE'
            """
                .formatted(identityPredicate),
            params,
            (rs, rowNum) ->
                new ContactChannelIdentity(
                    rs.getLong("contact_id"),
                    rs.getLong("identity_id"),
                    rs.getString("normalized_value"),
                    rs.getString("display_name")))
        .stream()
        .findFirst();
  }

  @Override
  public Map<Long, ContactChannelIdentity> loadDeterministicIdentities(
      long companyId, Collection<Long> contactIds, Channel channel) {
    if (contactIds == null || contactIds.isEmpty()) return Map.of();
    Map<String, Object> params =
        Map.of(
            "companyId", companyId,
            "contactIds", List.copyOf(contactIds),
            "identityType", identityType(channel));
    List<ContactChannelIdentity> rows =
        jdbcTemplate.query(
            """
            select c.id as contact_id, i.id as identity_id, i.normalized_value, c.display_name
            from contacts c
            join lateral (
              select ci.id, ci.normalized_value
              from contact_identities ci
              where ci.company_id = c.company_id
                and ci.contact_id = c.id
                and ci.status = 'ACTIVE'
                and ci.identity_type = :identityType
              order by ci.is_primary desc, ci.created_at asc, ci.id asc
              limit 1
            ) i on true
            where c.company_id = :companyId
              and c.status = 'ACTIVE'
              and c.id in (:contactIds)
            """,
            params,
            (rs, rowNum) ->
                new ContactChannelIdentity(
                    rs.getLong("contact_id"),
                    rs.getLong("identity_id"),
                    rs.getString("normalized_value"),
                    rs.getString("display_name")));
    Map<Long, ContactChannelIdentity> result = new HashMap<>();
    for (ContactChannelIdentity row : rows) result.put(row.contactId(), row);
    return result;
  }

  private String identityType(Channel channel) {
    return channel == Channel.EMAIL ? "EMAIL" : "PHONE";
  }
}

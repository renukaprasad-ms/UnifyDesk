package com.messaging.inbox.service;

import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import com.messaging.contacts.core.exception.ContactCoreException;
import com.messaging.contacts.core.service.ContactIdentityLookup;
import com.messaging.contacts.core.service.IdentityNormalizer;
import com.messaging.inbox.enums.InboxParticipantType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboxParticipantContactLinkService {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final IdentityNormalizer identityNormalizer;
  private final ContactIdentityLookup identityLookup;

  @Transactional
  public Link resolve(
      long companyId,
      long conversationId,
      InboxParticipantType participantType,
      String participantAddress) {
    IdentityType identityType = identityType(participantType);
    String addressKey = addressKey(participantType, participantAddress);
    if (addressKey == null) return null;

    String normalizedIdentity = normalize(identityType, participantAddress);
    Long contactId = null;
    Long contactIdentityId = null;
    Resolution resolution = Resolution.UNMATCHED;

    if (normalizedIdentity != null) {
      var resolved =
          identityLookup.findExact(companyId, identityType, null, normalizedIdentity).orElse(null);
      if (resolved != null) {
        contactId = resolved.contactId();
        contactIdentityId = resolved.identityId();
        resolution = Resolution.MATCHED;
      }
    }

    Instant now = Instant.now();
    jdbcTemplate
        .getJdbcTemplate()
        .update(
            """
            insert into inbox_participant_contact_links(
              id,company_id,conversation_id,participant_address,identity_type,
              normalized_identity,contact_id,contact_identity_id,resolution,resolved_at,
              created_at,updated_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?)
            on conflict (company_id,conversation_id,identity_type,participant_address)
            do update set
              normalized_identity=excluded.normalized_identity,
              contact_id=excluded.contact_id,
              contact_identity_id=excluded.contact_identity_id,
              resolution=excluded.resolution,
              resolved_at=excluded.resolved_at,
              updated_at=excluded.updated_at
            """,
            IdGenerator.nextId(),
            companyId,
            conversationId,
            addressKey,
            identityType.name(),
            normalizedIdentity,
            contactId,
            contactIdentityId,
            resolution.name(),
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now));

    return new Link(
        conversationId,
        addressKey,
        identityType,
        normalizedIdentity,
        contactId,
        contactIdentityId,
        resolution,
        now);
  }

  @Transactional(readOnly = true)
  public Map<LinkKey, Link> loadForConversations(long companyId, Collection<Long> conversationIds) {
    if (conversationIds == null || conversationIds.isEmpty()) return Map.of();

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("companyId", companyId)
            .addValue("conversationIds", conversationIds);
    Map<LinkKey, Link> result = new HashMap<>();
    jdbcTemplate
        .query(
            """
            select conversation_id,participant_address,identity_type,normalized_identity,
                   contact_id,contact_identity_id,resolution,resolved_at
            from inbox_participant_contact_links
            where company_id=:companyId
              and conversation_id in (:conversationIds)
            """,
            params,
            (rs, rowNum) ->
                new Link(
                    rs.getLong("conversation_id"),
                    rs.getString("participant_address"),
                    IdentityType.valueOf(rs.getString("identity_type")),
                    rs.getString("normalized_identity"),
                    (Long) rs.getObject("contact_id"),
                    (Long) rs.getObject("contact_identity_id"),
                    Resolution.valueOf(rs.getString("resolution")),
                    rs.getTimestamp("resolved_at").toInstant()))
        .forEach(
            link ->
                result.put(
                    key(link.conversationId(), link.identityType(), link.address()), link));
    return Map.copyOf(result);
  }

  public Link find(
      Map<LinkKey, Link> links,
      long conversationId,
      InboxParticipantType participantType,
      String participantAddress) {
    if (links == null || links.isEmpty()) return null;
    IdentityType type = identityType(participantType);
    String address = addressKey(participantType, participantAddress);
    return address == null ? null : links.get(key(conversationId, type, address));
  }

  private String normalize(IdentityType type, String address) {
    try {
      String candidate = address == null ? null : address.trim();
      if (type == IdentityType.PHONE
          && candidate != null
          && !candidate.startsWith("+")
          && candidate.matches("[0-9]{7,15}")) {
        candidate = "+" + candidate;
      }
      return identityNormalizer.normalize(type, null, candidate, null).normalizedValue();
    } catch (ContactCoreException exception) {
      return null;
    }
  }

  private IdentityType identityType(InboxParticipantType participantType) {
    if (participantType == InboxParticipantType.EMAIL_ADDRESS) return IdentityType.EMAIL;
    if (participantType == InboxParticipantType.PHONE_NUMBER) return IdentityType.PHONE;
    throw new IllegalArgumentException("Unsupported Inbox participant type");
  }

  private String addressKey(InboxParticipantType type, String address) {
    if (address == null || address.isBlank()) return null;
    String value = address.trim();
    if (value.length() > 320) return null;
    return type == InboxParticipantType.EMAIL_ADDRESS ? value.toLowerCase(Locale.ROOT) : value;
  }

  private LinkKey key(long conversationId, IdentityType type, String address) {
    return new LinkKey(conversationId, type, address);
  }

  public enum Resolution {
    MATCHED,
    UNMATCHED,
    AMBIGUOUS
  }

  public record Link(
      long conversationId,
      String address,
      IdentityType identityType,
      String normalizedIdentity,
      Long contactId,
      Long contactIdentityId,
      Resolution resolution,
      Instant resolvedAt) {}

  public record LinkKey(long conversationId, IdentityType identityType, String address) {}
}

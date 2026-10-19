package com.messaging.contacts.integration;

import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import com.messaging.contacts.consent.ContactPreferenceProjectionPolicy;
import com.messaging.contacts.core.service.ContactMergeExtension;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reconciles mutable consent/suppression projections during an explicit Contact merge.
 *
 * <p>Append-only preference/suppression event history is intentionally not rewritten.
 */
@Component
@RequiredArgsConstructor
public class ContactConsentMergeExtension implements ContactMergeExtension {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void reconcile(
      long companyId, long sourceContactId, long targetContactId, long actorUserId) {
    reconcilePreferences(companyId, sourceContactId, targetContactId);
    jdbcTemplate.update(
        """
        update contact_suppressions
        set contact_id = ?, updated_at = now(), version = version + 1
        where company_id = ? and contact_id = ?
        """,
        targetContactId,
        companyId,
        sourceContactId);
  }

  private void reconcilePreferences(long companyId, long sourceContactId, long targetContactId) {
    List<PreferenceRow> source =
        jdbcTemplate.query(
            """
            select id, identity_id, channel, purpose, state, source, source_ref, occurred_at
            from contact_channel_preferences
            where company_id = ? and contact_id = ?
            order by id
            for update
            """,
            this::map,
            companyId,
            sourceContactId);

    for (PreferenceRow sourceRow : source) {
      PreferenceRow target =
          jdbcTemplate
              .query(
                  """
                  select id, identity_id, channel, purpose, state, source, source_ref, occurred_at
                  from contact_channel_preferences
                  where company_id = ?
                    and contact_id = ?
                    and identity_id is not distinct from ?
                    and channel = ?
                    and purpose = ?
                  for update
                  """,
                  this::map,
                  companyId,
                  targetContactId,
                  sourceRow.identityId(),
                  sourceRow.channel(),
                  sourceRow.purpose())
              .stream()
              .findFirst()
              .orElse(null);

      if (target == null) {
        jdbcTemplate.update(
            """
            update contact_channel_preferences
            set contact_id = ?, updated_at = now(), version = version + 1
            where company_id = ? and id = ?
            """,
            targetContactId,
            companyId,
            sourceRow.id());
        continue;
      }

      PreferenceRow winner = restrictive(sourceRow, target);
      jdbcTemplate.update(
          """
          update contact_channel_preferences
          set state = ?, source = ?, source_ref = ?, occurred_at = ?,
              updated_at = now(), version = version + 1
          where company_id = ? and id = ?
          """,
          winner.state(),
          winner.source(),
          winner.sourceRef(),
          java.sql.Timestamp.from(winner.occurredAt()),
          companyId,
          target.id());
      jdbcTemplate.update(
          "delete from contact_channel_preferences where company_id = ? and id = ?",
          companyId,
          sourceRow.id());
    }
  }

  /**
   * Merge the effective projections using the same event-time policy as normal preference mutation.
   * Newer evidence wins; equal-time conflicts fail closed.
   */
  private PreferenceRow restrictive(PreferenceRow left, PreferenceRow right) {
    return ContactPreferenceProjectionPolicy.shouldReplace(
            PreferenceState.valueOf(right.state()),
            right.occurredAt(),
            PreferenceState.valueOf(left.state()),
            left.occurredAt())
        ? left
        : right;
  }

  private PreferenceRow map(ResultSet rs, int rowNum) throws SQLException {
    return new PreferenceRow(
        rs.getLong("id"),
        (Long) rs.getObject("identity_id"),
        rs.getString("channel"),
        rs.getString("purpose"),
        rs.getString("state"),
        rs.getString("source"),
        rs.getString("source_ref"),
        rs.getTimestamp("occurred_at").toInstant());
  }

  private record PreferenceRow(
      long id,
      Long identityId,
      String channel,
      String purpose,
      String state,
      String source,
      String sourceRef,
      Instant occurredAt) {}
}

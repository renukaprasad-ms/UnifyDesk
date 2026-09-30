package com.messaging.contacts.audience;

import com.messaging.contacts.consent.ConsentTypes.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class AudienceSnapshotRepository {

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public Snapshot create(
      long id,
      long companyId,
      String sourceType,
      long sourceId,
      String sourceVersion,
      String filterHash,
      Channel channel,
      Purpose purpose,
      Long asyncJobId) {
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into contact_audience_snapshots (
          id, company_id, source_type, source_id, source_version, filter_hash, channel,
          purpose, snapshot_at, status, async_job_id, created_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PREPARING', ?, ?)
        """,
        id,
        companyId,
        sourceType,
        sourceId,
        sourceVersion,
        filterHash,
        channel.name(),
        purpose.name(),
        Timestamp.from(now),
        asyncJobId,
        Timestamp.from(now));
    return require(companyId, id);
  }

  public Snapshot require(long companyId, long snapshotId) {
    return jdbcTemplate
        .query(
            "select * from contact_audience_snapshots where company_id = ? and id = ?",
            this::map,
            companyId,
            snapshotId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND"));
  }

  public Snapshot requireForUpdate(long companyId, long snapshotId) {
    return jdbcTemplate
        .query(
            "select * from contact_audience_snapshots where company_id = ? and id = ? for update",
            this::map,
            companyId,
            snapshotId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new com.messaging.common.exception.NotFoundException("CONTACT_NOT_FOUND"));
  }

  public int insertMembers(long snapshotId, long companyId, List<Member> members) {
    if (members.isEmpty()) return 0;
    Instant now = Instant.now();
    int[] counts =
        jdbcTemplate.batchUpdate(
            """
            insert into contact_audience_snapshot_members (
              snapshot_id, company_id, contact_id, identity_id, normalized_destination,
              display_name_snapshot, eligibility_version, eligibility_reason, sequence_id, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict do nothing
            """,
            new BatchPreparedStatementSetter() {
              @Override
              public void setValues(PreparedStatement ps, int index) throws SQLException {
                Member member = members.get(index);
                ps.setLong(1, snapshotId);
                ps.setLong(2, companyId);
                ps.setLong(3, member.contactId());
                ps.setLong(4, member.identityId());
                ps.setString(5, member.normalizedDestination());
                ps.setString(6, member.displayName());
                ps.setString(7, member.eligibilityVersion());
                ps.setString(8, member.eligibilityReason());
                ps.setLong(9, member.sequenceId());
                ps.setTimestamp(10, Timestamp.from(now));
              }

              @Override
              public int getBatchSize() {
                return members.size();
              }
            });
    int inserted = 0;
    for (int count : counts)
      if (count > 0 || count == Statement.SUCCESS_NO_INFO) inserted++;
    return inserted;
  }

  @Transactional
  public void advance(
      long companyId,
      long snapshotId,
      long expectedCursor,
      long newCursor,
      long candidateDelta,
      long eligibleDelta) {
    int updated =
        jdbcTemplate.update(
            """
            update contact_audience_snapshots
            set cursor = ?, candidate_count = candidate_count + ?,
                eligible_count = eligible_count + ?
            where company_id = ? and id = ? and status = 'PREPARING' and cursor = ?
            """,
            newCursor,
            candidateDelta,
            eligibleDelta,
            companyId,
            snapshotId,
            expectedCursor);
    if (updated != 1) throw new IllegalStateException("Snapshot cursor conflict");
  }

  @Transactional
  public void ready(long companyId, long snapshotId) {
    jdbcTemplate.update(
        """
        update contact_audience_snapshots
        set status = 'READY', completed_at = ?
        where company_id = ? and id = ? and status = 'PREPARING'
        """,
        Timestamp.from(Instant.now()),
        companyId,
        snapshotId);
  }

  private Snapshot map(ResultSet rs, int rowNum) throws SQLException {
    Timestamp completed = rs.getTimestamp("completed_at");
    return new Snapshot(
        rs.getLong("id"),
        rs.getLong("company_id"),
        rs.getString("source_type"),
        rs.getLong("source_id"),
        rs.getString("source_version"),
        rs.getString("filter_hash"),
        Channel.valueOf(rs.getString("channel")),
        Purpose.valueOf(rs.getString("purpose")),
        rs.getString("status"),
        rs.getLong("candidate_count"),
        rs.getLong("eligible_count"),
        rs.getLong("cursor"),
        (Long) rs.getObject("async_job_id"),
        rs.getTimestamp("snapshot_at").toInstant(),
        completed == null ? null : completed.toInstant());
  }

  public record Snapshot(
      long id,
      long companyId,
      String sourceType,
      long sourceId,
      String sourceVersion,
      String filterHash,
      Channel channel,
      Purpose purpose,
      String status,
      long candidateCount,
      long eligibleCount,
      long cursor,
      Long asyncJobId,
      Instant snapshotAt,
      Instant completedAt) {}

  public record Member(
      long contactId,
      long identityId,
      String normalizedDestination,
      String displayName,
      String eligibilityVersion,
      String eligibilityReason,
      long sequenceId) {}
}

package com.messaging.crm;

import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrmDealAttachmentService {

  private final JdbcTemplate jdbc;
  private final CrmDealsService dealsService;

  public record DealAttachmentDto(
      String id,
      String dealId,
      String mediaId,
      String filename,
      String contentType,
      long sizeBytes,
      String uploaderUserId,
      String uploaderName,
      Instant createdAt) {}

  public record AttachInput(
      Long mediaId,
      String filename,
      String contentType,
      Long sizeBytes) {}

  @Transactional(readOnly = true)
  public List<DealAttachmentDto> list(long companyId, long dealId) {
    // Verify deal exists
    dealsService.get(companyId, dealId);

    String sql =
        """
        select a.*, u.name as uploader_name
        from crm_deal_attachments a
        left join users u on u.id = a.uploader_user_id
        where a.company_id = ? and a.deal_id = ?
        order by a.created_at desc
        """;

    return jdbc.query(sql, this::mapAttachment, companyId, dealId);
  }

  @Transactional
  public DealAttachmentDto attach(
      long companyId, long dealId, AttachInput input, long actorUserId) {
    if (input.mediaId() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_ID_REQUIRED");
    }

    // Verify deal exists and get current stage for event logging
    CrmDealsService.Deal deal = dealsService.get(companyId, dealId);

    // Validate media exists
    var mediaRows =
        jdbc.query(
            "select id, original_file_name, content_type, size_bytes, company_id from media where id = ?",
            (rs, row) ->
                new MediaRecord(
                    rs.getLong("id"),
                    rs.getString("original_file_name"),
                    rs.getString("content_type"),
                    rs.getLong("size_bytes"),
                    rs.getObject("company_id") != null ? rs.getLong("company_id") : null),
            input.mediaId());

    if (mediaRows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND");
    }
    MediaRecord media = mediaRows.getFirst();

    // Verify media belongs to this company (or update if unassigned)
    if (media.companyId() != null && media.companyId() != companyId) {
      throw new ApiException(HttpStatus.FORBIDDEN, "MEDIA_COMPANY_MISMATCH");
    }
    if (media.companyId() == null) {
      jdbc.update("update media set company_id = ? where id = ?", companyId, input.mediaId());
    }

    String filename =
        input.filename() != null && !input.filename().isBlank()
            ? input.filename().trim()
            : media.originalFileName();
    if (filename == null || filename.isBlank()) {
      filename = "document_" + input.mediaId();
    }
    if (filename.length() > 255) {
      filename = filename.substring(0, 255);
    }

    String contentType =
        input.contentType() != null && !input.contentType().isBlank()
            ? input.contentType().trim()
            : media.contentType();
    if (contentType != null && contentType.length() > 100) {
      contentType = contentType.substring(0, 100);
    }

    long sizeBytes = input.sizeBytes() != null && input.sizeBytes() > 0 ? input.sizeBytes() : media.sizeBytes();

    long attachmentId = IdGenerator.nextId();
    Timestamp now = Timestamp.from(Instant.now());

    jdbc.update(
        """
        insert into crm_deal_attachments
          (id, company_id, deal_id, media_id, filename, content_type, size_bytes, uploader_user_id, created_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        attachmentId,
        companyId,
        dealId,
        input.mediaId(),
        filename,
        contentType,
        sizeBytes,
        actorUserId,
        now);

    // Log deal activity event
    long eventId = IdGenerator.nextId();
    jdbc.update(
        """
        insert into crm_deal_events(id, company_id, deal_id, event_type, previous_stage, new_stage, changed_fields, actor_user_id, occurred_at)
        values(?, ?, ?, 'UPDATED', ?, ?, ?, ?, ?)
        """,
        eventId,
        companyId,
        dealId,
        deal.stage(),
        deal.stage(),
        "attached_document: " + filename,
        actorUserId,
        now);

    return get(companyId, dealId, attachmentId);
  }

  @Transactional(readOnly = true)
  public DealAttachmentDto get(long companyId, long dealId, long attachmentId) {
    String sql =
        """
        select a.*, u.name as uploader_name
        from crm_deal_attachments a
        left join users u on u.id = a.uploader_user_id
        where a.company_id = ? and a.deal_id = ? and a.id = ?
        """;
    var rows = jdbc.query(sql, this::mapAttachment, companyId, dealId, attachmentId);
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND");
    }
    return rows.getFirst();
  }

  @Transactional
  public void delete(long companyId, long dealId, long attachmentId, long actorUserId) {
    DealAttachmentDto existing = get(companyId, dealId, attachmentId);
    CrmDealsService.Deal deal = dealsService.get(companyId, dealId);

    jdbc.update(
        "delete from crm_deal_attachments where company_id = ? and deal_id = ? and id = ?",
        companyId,
        dealId,
        attachmentId);

    // Log deal activity event
    long eventId = IdGenerator.nextId();
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        """
        insert into crm_deal_events(id, company_id, deal_id, event_type, previous_stage, new_stage, changed_fields, actor_user_id, occurred_at)
        values(?, ?, ?, 'UPDATED', ?, ?, ?, ?, ?)
        """,
        eventId,
        companyId,
        dealId,
        deal.stage(),
        deal.stage(),
        "removed_document: " + existing.filename(),
        actorUserId,
        now);
  }

  private DealAttachmentDto mapAttachment(ResultSet rs, int rowNum) throws SQLException {
    return new DealAttachmentDto(
        String.valueOf(rs.getLong("id")),
        String.valueOf(rs.getLong("deal_id")),
        String.valueOf(rs.getLong("media_id")),
        rs.getString("filename"),
        rs.getString("content_type"),
        rs.getLong("size_bytes"),
        rs.getObject("uploader_user_id") != null ? String.valueOf(rs.getLong("uploader_user_id")) : null,
        rs.getString("uploader_name"),
        rs.getTimestamp("created_at").toInstant());
  }

  private record MediaRecord(
      long id,
      String originalFileName,
      String contentType,
      long sizeBytes,
      Long companyId) {}
}

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
public class CrmTaskAttachmentService {

  private final JdbcTemplate jdbc;
  private final CrmWorkService workService;

  public record TaskAttachmentDto(
      String id,
      String taskId,
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
  public List<TaskAttachmentDto> list(long companyId, long taskId) {
    workService.getTask(companyId, taskId);
    String sql =
        """
        select a.*, u.name as uploader_name
        from crm_task_attachments a
        left join users u on u.id = a.uploader_user_id
        where a.company_id = ? and a.task_id = ?
        order by a.created_at desc
        """;
    return jdbc.query(sql, this::mapAttachment, companyId, taskId);
  }

  @Transactional
  public TaskAttachmentDto attach(
      long companyId, long taskId, AttachInput input, long actorUserId) {
    if (input.mediaId() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_ATTACHMENT_MEDIA_REQUIRED");
    }
    if (input.filename() == null || input.filename().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_ATTACHMENT_FILENAME_REQUIRED");
    }

    workService.getTask(companyId, taskId);

    long id = IdGenerator.nextId();
    String safeFilename = input.filename().trim();
    if (safeFilename.length() > 255) {
      safeFilename = safeFilename.substring(0, 255);
    }
    String safeContentType = input.contentType() != null && input.contentType().length() > 100
        ? input.contentType().substring(0, 100)
        : input.contentType();
    long size = input.sizeBytes() != null ? input.sizeBytes() : 0L;

    jdbc.update(
        """
        insert into crm_task_attachments(id, company_id, task_id, media_id, filename, content_type, size_bytes, uploader_user_id, created_at)
        values(?, ?, ?, ?, ?, ?, ?, ?, now())
        """,
        id,
        companyId,
        taskId,
        input.mediaId(),
        safeFilename,
        safeContentType,
        size,
        actorUserId);

    return get(companyId, taskId, id);
  }

  @Transactional(readOnly = true)
  public TaskAttachmentDto get(long companyId, long taskId, long attachmentId) {
    String sql =
        """
        select a.*, u.name as uploader_name
        from crm_task_attachments a
        left join users u on u.id = a.uploader_user_id
        where a.company_id = ? and a.task_id = ? and a.id = ?
        """;
    List<TaskAttachmentDto> rows = jdbc.query(sql, this::mapAttachment, companyId, taskId, attachmentId);
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "CRM_ATTACHMENT_NOT_FOUND");
    }
    return rows.getFirst();
  }

  @Transactional
  public void delete(long companyId, long taskId, long attachmentId, long actorUserId) {
    get(companyId, taskId, attachmentId);
    jdbc.update(
        "delete from crm_task_attachments where company_id = ? and task_id = ? and id = ?",
        companyId,
        taskId,
        attachmentId);
  }

  private TaskAttachmentDto mapAttachment(ResultSet rs, int row) throws SQLException {
    Timestamp crAt = rs.getTimestamp("created_at");
    Object uploaderId = rs.getObject("uploader_user_id");

    return new TaskAttachmentDto(
        rs.getString("id"),
        rs.getString("task_id"),
        rs.getString("media_id"),
        rs.getString("filename"),
        rs.getString("content_type"),
        rs.getLong("size_bytes"),
        uploaderId == null ? null : uploaderId.toString(),
        rs.getString("uploader_name"),
        crAt == null ? null : crAt.toInstant());
  }
}

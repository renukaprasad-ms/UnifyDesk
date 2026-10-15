package com.messaging.crm;

import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrmCommentService {

  private final JdbcTemplate jdbc;

  public record CommentDto(
      String id,
      String entityType,
      String entityId,
      String body,
      String authorUserId,
      String authorName,
      String authorEmail,
      Instant createdAt,
      Instant updatedAt) {}

  public record AddCommentInput(String body) {}

  @Transactional(readOnly = true)
  public List<CommentDto> listComments(long companyId, String entityType, long entityId) {
    validateType(entityType);
    return jdbc.query(
        """
        select c.id, c.entity_type, c.entity_id, c.body, c.author_user_id,
               c.created_at, c.updated_at,
               u.name as author_name, u.email as author_email
        from crm_comments c
        left join users u on u.id = c.author_user_id
        where c.company_id = ? and c.entity_type = ? and c.entity_id = ?
        order by c.created_at asc
        """,
        this::mapComment,
        companyId,
        entityType.toUpperCase(),
        entityId);
  }

  @Transactional
  public CommentDto addComment(
      long companyId, String entityType, long entityId, String body, long authorUserId) {
    validateType(entityType);
    if (body == null || body.trim().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Comment body cannot be empty");
    }

    long id = IdGenerator.nextId();
    Instant now = Instant.now();

    jdbc.update(
        """
        insert into crm_comments(id, company_id, entity_type, entity_id, body, author_user_id, created_at, updated_at)
        values(?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        companyId,
        entityType.toUpperCase(),
        entityId,
        body.trim(),
        authorUserId,
        now,
        now);

    return getComment(companyId, id);
  }

  @Transactional(readOnly = true)
  public CommentDto getComment(long companyId, long commentId) {
    List<CommentDto> list =
        jdbc.query(
            """
            select c.id, c.entity_type, c.entity_id, c.body, c.author_user_id,
                   c.created_at, c.updated_at,
                   u.name as author_name, u.email as author_email
            from crm_comments c
            left join users u on u.id = c.author_user_id
            where c.company_id = ? and c.id = ?
            """,
            this::mapComment,
            companyId,
            commentId);
    if (list.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Comment not found");
    }
    return list.getFirst();
  }

  @Transactional
  public void deleteComment(long companyId, long commentId, long actorUserId) {
    CommentDto existing = getComment(companyId, commentId);
    // Allow author or user with permissions to delete
    int rows =
        jdbc.update(
            "delete from crm_comments where company_id = ? and id = ?",
            companyId,
            commentId);
    if (rows == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Comment not found");
    }
  }

  private void validateType(String entityType) {
    if (entityType == null || !List.of("TASK", "DEAL", "LEAD").contains(entityType.toUpperCase())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid entity type: " + entityType);
    }
  }

  private CommentDto mapComment(ResultSet rs, int row) throws SQLException {
    return new CommentDto(
        String.valueOf(rs.getLong("id")),
        rs.getString("entity_type"),
        String.valueOf(rs.getLong("entity_id")),
        rs.getString("body"),
        rs.getObject("author_user_id") != null ? String.valueOf(rs.getLong("author_user_id")) : null,
        rs.getString("author_name"),
        rs.getString("author_email"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}

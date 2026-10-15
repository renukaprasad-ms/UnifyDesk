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
public class CrmSubtaskService {

  private final JdbcTemplate jdbc;
  private final CrmWorkService workService;

  public record Subtask(
      String id,
      String taskId,
      String title,
      boolean completed,
      int position,
      String completedByUserId,
      String completedByName,
      Instant completedAt,
      Instant createdAt,
      Instant updatedAt) {}

  public record CreateSubtaskInput(String title) {}

  public record UpdateSubtaskInput(String title) {}

  public record ToggleSubtaskInput(boolean completed) {}

  public record ReorderSubtasksInput(List<Long> subtaskIds) {}

  @Transactional(readOnly = true)
  public List<Subtask> list(long companyId, long taskId) {
    workService.getTask(companyId, taskId);
    String sql =
        """
        select s.*, u.name as completed_by_name
        from crm_subtasks s
        left join users u on u.id = s.completed_by_user_id
        where s.company_id = ? and s.task_id = ?
        order by s.position asc, s.id asc
        """;
    return jdbc.query(sql, this::mapSubtask, companyId, taskId);
  }

  @Transactional
  public Subtask create(long companyId, long taskId, String title, long actorUserId) {
    workService.getTask(companyId, taskId);
    if (title == null || title.trim().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_SUBTASK_TITLE_REQUIRED");
    }
    String safeTitle = title.trim();
    if (safeTitle.length() > 300) {
      safeTitle = safeTitle.substring(0, 300);
    }

    Integer maxPos =
        jdbc.queryForObject(
            "select coalesce(max(position), -1) from crm_subtasks where company_id = ? and task_id = ?",
            Integer.class,
            companyId,
            taskId);
    int nextPos = (maxPos == null ? 0 : maxPos + 1);

    long id = IdGenerator.nextId();
    Instant now = Instant.now();

    jdbc.update(
        """
        insert into crm_subtasks(id, company_id, task_id, title, completed, position, created_at, updated_at)
        values(?, ?, ?, ?, false, ?, ?, ?)
        """,
        id,
        companyId,
        taskId,
        safeTitle,
        nextPos,
        Timestamp.from(now),
        Timestamp.from(now));

    return get(companyId, taskId, id);
  }

  @Transactional(readOnly = true)
  public Subtask get(long companyId, long taskId, long subtaskId) {
    String sql =
        """
        select s.*, u.name as completed_by_name
        from crm_subtasks s
        left join users u on u.id = s.completed_by_user_id
        where s.company_id = ? and s.task_id = ? and s.id = ?
        """;
    List<Subtask> rows = jdbc.query(sql, this::mapSubtask, companyId, taskId, subtaskId);
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "CRM_SUBTASK_NOT_FOUND");
    }
    return rows.getFirst();
  }

  @Transactional
  public Subtask toggle(long companyId, long taskId, long subtaskId, boolean completed, long actorUserId) {
    get(companyId, taskId, subtaskId);
    Instant now = Instant.now();
    jdbc.update(
        """
        update crm_subtasks
        set completed = ?,
            completed_by_user_id = case when ? = true then ? else null end,
            completed_at = case when ? = true then ? else null end,
            updated_at = ?
        where company_id = ? and task_id = ? and id = ?
        """,
        completed,
        completed,
        actorUserId,
        completed,
        Timestamp.from(now),
        Timestamp.from(now),
        companyId,
        taskId,
        subtaskId);

    return get(companyId, taskId, subtaskId);
  }

  @Transactional
  public Subtask update(long companyId, long taskId, long subtaskId, String title, long actorUserId) {
    get(companyId, taskId, subtaskId);
    if (title == null || title.trim().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_SUBTASK_TITLE_REQUIRED");
    }
    String safeTitle = title.trim();
    if (safeTitle.length() > 300) {
      safeTitle = safeTitle.substring(0, 300);
    }

    jdbc.update(
        "update crm_subtasks set title = ?, updated_at = now() where company_id = ? and task_id = ? and id = ?",
        safeTitle,
        companyId,
        taskId,
        subtaskId);

    return get(companyId, taskId, subtaskId);
  }

  @Transactional
  public void delete(long companyId, long taskId, long subtaskId, long actorUserId) {
    get(companyId, taskId, subtaskId);
    jdbc.update(
        "delete from crm_subtasks where company_id = ? and task_id = ? and id = ?",
        companyId,
        taskId,
        subtaskId);
  }

  @Transactional
  public void reorder(long companyId, long taskId, List<Long> subtaskIds, long actorUserId) {
    if (subtaskIds == null || subtaskIds.isEmpty()) return;
    for (int i = 0; i < subtaskIds.size(); i++) {
      jdbc.update(
          "update crm_subtasks set position = ?, updated_at = now() where company_id = ? and task_id = ? and id = ?",
          i,
          companyId,
          taskId,
          subtaskIds.get(i));
    }
  }

  private Subtask mapSubtask(ResultSet rs, int row) throws SQLException {
    Timestamp compAt = rs.getTimestamp("completed_at");
    Timestamp crAt = rs.getTimestamp("created_at");
    Timestamp upAt = rs.getTimestamp("updated_at");
    Object compUserId = rs.getObject("completed_by_user_id");

    return new Subtask(
        rs.getString("id"),
        rs.getString("task_id"),
        rs.getString("title"),
        rs.getBoolean("completed"),
        rs.getInt("position"),
        compUserId == null ? null : compUserId.toString(),
        rs.getString("completed_by_name"),
        compAt == null ? null : compAt.toInstant(),
        crAt == null ? null : crAt.toInstant(),
        upAt == null ? null : upAt.toInstant());
  }
}

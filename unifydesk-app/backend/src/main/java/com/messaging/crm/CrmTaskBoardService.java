package com.messaging.crm;

import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrmTaskBoardService {
  private final JdbcTemplate jdbc;

  public record Column(
      String key,
      String name,
      String statusGroup,
      int position,
      boolean systemColumn,
      long version) {}

  public record Order(String key, long version) {}

  static void defaults(JdbcTemplate jdbc, long company) {
    jdbc.update(
        """
        insert into crm_task_columns(company_id,column_key,name,status_group,position,system_column)
        select ?,d.key,d.name,d.key,d.position,true from
          (values ('OPEN','To do',0),('IN_PROGRESS','In progress',1),('DONE','Done',2),('CANCELLED','Cancelled',3)) d(key,name,position)
        where not exists(select 1 from crm_task_columns c where c.company_id=? and c.column_key=d.key)
        on conflict(company_id,column_key) do nothing
        """,
        company,
        company);
  }

  @Transactional
  public List<Column> columns(long company) {
    defaults(jdbc, company);
    return rows(company, false);
  }

  private List<Column> rows(long company, boolean lock) {
    return jdbc.query(
        "select * from crm_task_columns where company_id=? order by position,column_key"
            + (lock ? " for update" : ""),
        (rs, row) ->
            new Column(
                rs.getString("column_key"),
                rs.getString("name"),
                rs.getString("status_group"),
                rs.getInt("position"),
                rs.getBoolean("system_column"),
                rs.getLong("version")),
        company);
  }

  static Column target(JdbcTemplate jdbc, long company, String key) {
    defaults(jdbc, company);
    var rows =
        jdbc.query(
            "select * from crm_task_columns where company_id=? and column_key=? for share",
            (rs, row) ->
                new Column(
                    rs.getString("column_key"),
                    rs.getString("name"),
                    rs.getString("status_group"),
                    rs.getInt("position"),
                    rs.getBoolean("system_column"),
                    rs.getLong("version")),
            company,
            key);
    if (rows.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_TASK_STEP_INVALID");
    return rows.getFirst();
  }

  private void lock(long company) {
    jdbc.queryForList("select id from companies where id=? for update", Long.class, company);
    defaults(jdbc, company);
  }

  private static String name(String name) {
    if (name == null || name.isBlank() || name.trim().length() > 80)
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_TASK_STEP_NAME_INVALID");
    return name.trim();
  }

  @Transactional
  public List<Column> create(long company, String name, String group) {
    lock(company);
    String label = name(name);
    if (group == null || !List.of("OPEN", "IN_PROGRESS", "DONE", "CANCELLED").contains(group))
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_TASK_STEP_GROUP_INVALID");
    var columns = rows(company, false);
    if (columns.size() >= 20) throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_TASK_STEP_LIMIT");
    int position = columns.stream().mapToInt(Column::position).max().orElse(-1) + 1;
    try {
      jdbc.update(
          "insert into crm_task_columns(company_id,column_key,name,status_group,position) values(?,?,?,?,?)",
          company,
          "STEP_" + IdGenerator.nextId(),
          label,
          group,
          position);
    } catch (DuplicateKeyException e) {
      throw new ApiException(HttpStatus.CONFLICT, "CRM_TASK_STEP_NAME_EXISTS");
    }
    return rows(company, false);
  }

  @Transactional
  public List<Column> rename(long company, String key, long version, String name) {
    lock(company);
    try {
      int count =
          jdbc.update(
              "update crm_task_columns set name=?,version=version+1 where company_id=? and column_key=? and version=?",
              name(name),
              company,
              key,
              version);
      if (count != 1) throw conflict();
    } catch (DuplicateKeyException e) {
      throw new ApiException(HttpStatus.CONFLICT, "CRM_TASK_STEP_NAME_EXISTS");
    }
    return rows(company, false);
  }

  @Transactional
  public List<Column> reorder(long company, List<Order> order) {
    lock(company);
    var columns = rows(company, true);
    if (order == null
        || order.size() != columns.size()
        || order.stream().anyMatch(e -> e == null || e.key() == null)
        || new HashSet<>(order.stream().map(Order::key).toList()).size() != order.size())
      throw conflict();
    for (int index = 0; index < order.size(); index++) {
      Order entry = order.get(index);
      if (columns.stream()
          .noneMatch(c -> c.key().equals(entry.key()) && c.version() == entry.version()))
        throw conflict();
    }
    for (int index = 0; index < order.size(); index++)
      jdbc.update(
          "update crm_task_columns set position=?,version=version+1 where company_id=? and column_key=?",
          index,
          company,
          order.get(index).key());
    return rows(company, false);
  }

  @Transactional
  public List<Column> remove(long company, String key, long version) {
    lock(company);
    Column column =
        rows(company, true).stream()
            .filter(c -> c.key().equals(key))
            .findFirst()
            .orElseThrow(CrmTaskBoardService::conflict);
    if (column.version() != version) throw conflict();
    if (column.systemColumn())
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_TASK_DEFAULT_STEP_REQUIRED");
    long count =
        jdbc.queryForObject(
            "select count(*) from crm_tasks where company_id=? and column_key=?",
            Long.class,
            company,
            key);
    if (count > 0) throw new ApiException(HttpStatus.CONFLICT, "CRM_TASK_STEP_NOT_EMPTY");
    jdbc.update("delete from crm_task_columns where company_id=? and column_key=?", company, key);
    return rows(company, false);
  }

  private static ApiException conflict() {
    return new ApiException(HttpStatus.CONFLICT, "CRM_TASK_BOARD_VERSION_CONFLICT");
  }
}

package com.messaging.crm;

import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.repository.CompanyMemberRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrmWorkService {
  private final JdbcTemplate jdbc;
  private final CompanyMemberRepository members;

  public record Account(String id, String name, String website, String notes,
                        String ownerUserId, long version, Instant createdAt) {}
  public record Task(String id, String title, String details, String status,
                     String priority, Instant dueAt, String assigneeUserId,
                     String contactId, String accountId, long version) {}
  public record Page<T>(List<T> items, String nextCursor, boolean hasMore) {}
  public record AccountContact(String contactId, String displayName) {}
  /** A lightweight CRM projection; message activity remains owned by Contacts/Inbox/Broadcast. */
  public record LeadState(String stage, long version) {}
  public record Contact360(LeadState lead, List<Account> accounts, List<Task> tasks,
                           boolean moreAccounts, boolean moreTasks) {}

  private static String id(ResultSet rs, String field) throws SQLException {
    Object value = rs.getObject(field);
    return value == null ? null : value.toString();
  }

  private static Account account(ResultSet rs, int row) throws SQLException {
    return new Account(id(rs, "id"), rs.getString("name"), rs.getString("website"),
        rs.getString("notes"), id(rs, "owner_user_id"), rs.getLong("version"),
        rs.getTimestamp("created_at").toInstant());
  }

  private static Task task(ResultSet rs, int row) throws SQLException {
    var due = rs.getTimestamp("due_at");
    return new Task(id(rs, "id"), rs.getString("title"), rs.getString("details"),
        rs.getString("status"), rs.getString("priority"),
        due == null ? null : due.toInstant(), id(rs, "assignee_user_id"),
        id(rs, "contact_id"), id(rs, "account_id"), rs.getLong("version"));
  }

  private static String trimmed(String value, int max) {
    if (value == null) return null;
    String normalized = value.trim();
    if (normalized.length() > max) throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_FIELD_TOO_LONG");
    return normalized.isBlank() ? null : normalized;
  }

  private void activeMember(long companyId, long userId) {
    if (!members.existsByCompanyIdAndUserIdAndStatus(
        companyId, userId, CompanyMemberStatus.ACTIVE)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_ASSIGNEE_NOT_ACTIVE");
    }
    // Lock membership and user rows until task commit. This prevents assignments to
    // deactivated/deleting users and coordinates with concurrent offboarding.
    var locked = jdbc.query(
        """
        select m.user_id from company_members m
        join users u on u.id = m.user_id and u.status = 'ACTIVE'
        join companies c on c.id = m.company_id and c.status = 'ACTIVE'
        where m.company_id = ? and m.user_id = ? and m.status = 'ACTIVE'
        for share of m, u
        """, (rs, row) -> rs.getLong("user_id"), companyId, userId);
    if (locked.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_ASSIGNEE_NOT_ACTIVE");
    }
  }

  private void requireContact(long companyId, Long contactId) {
    if (contactId == null) return;
    Integer count = jdbc.queryForObject(
        "select count(*) from contacts where company_id = ? and id = ? and status = 'ACTIVE'",
        Integer.class, companyId, contactId);
    if (count == null || count == 0) throw new ApiException(HttpStatus.NOT_FOUND, "CRM_CONTACT_NOT_FOUND");
  }

  private void requireAccount(long companyId, Long accountId) {
    if (accountId == null) return;
    Integer count = jdbc.queryForObject(
        "select count(*) from crm_accounts where company_id = ? and id = ?",
        Integer.class, companyId, accountId);
    if (count == null || count == 0) throw new ApiException(HttpStatus.NOT_FOUND, "CRM_ACCOUNT_NOT_FOUND");
  }

  @Transactional(readOnly = true)
  public Contact360 contact360(long companyId, long contactId) {
    requireContact(companyId, contactId);
    List<LeadState> leadRows = jdbc.query(
        "select stage, version from crm_leads where company_id = ? and contact_id = ?",
        (rs, row) -> new LeadState(rs.getString("stage"), rs.getLong("version")),
        companyId, contactId);
    List<Account> accountRows = jdbc.query("""
        select a.* from crm_accounts a
        join crm_account_contacts l
          on l.company_id = a.company_id and l.account_id = a.id
        where l.company_id = ? and l.contact_id = ?
        order by a.id desc limit 21
        """, CrmWorkService::account, companyId, contactId);
    List<Task> taskRows = jdbc.query("""
        select * from crm_tasks
        where company_id = ? and contact_id = ?
        order by id desc limit 21
        """, CrmWorkService::task, companyId, contactId);
    return new Contact360(
        leadRows.isEmpty() ? null : leadRows.getFirst(),
        accountRows.size() > 20 ? List.copyOf(accountRows.subList(0, 20)) : accountRows,
        taskRows.size() > 20 ? List.copyOf(taskRows.subList(0, 20)) : taskRows,
        accountRows.size() > 20,
        taskRows.size() > 20);
  }

  @Transactional
  public void unlinkContact(long companyId, long accountId, long contactId) {
    requireAccount(companyId, accountId);
    jdbc.update("""
        delete from crm_account_contacts
        where company_id = ? and account_id = ? and contact_id = ?
        """, companyId, accountId, contactId);
  }

  @Transactional(readOnly = true)
  public Page<Account> accounts(long companyId, Long before, int requestedSize) {
    int size = Math.max(1, Math.min(100, requestedSize));
    List<Account> results = jdbc.query(
        "select * from crm_accounts where company_id = ? and (cast(? as bigint) is null or id < ?) order by id desc limit ?",
        CrmWorkService::account, companyId, before, before, size + 1);
    boolean more = results.size() > size;
    List<Account> page = more ? results.subList(0, size) : results;
    return new Page<>(page, more ? page.getLast().id() : null, more);
  }

  @Transactional
  public Account createAccount(long companyId, String name, String website, String notes, Long owner) {
    String safeName = trimmed(name, 240);
    if (safeName == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_ACCOUNT_NAME_REQUIRED");
    if (owner != null) activeMember(companyId, owner);
    long key = IdGenerator.nextId();
    try {
      jdbc.update("""
          insert into crm_accounts(id,company_id,name,normalized_name,website,notes,owner_user_id)
          values (?, ?, ?, ?, ?, ?, ?)
          """, key, companyId, safeName, safeName.toLowerCase(java.util.Locale.ROOT),
          trimmed(website, 320), trimmed(notes, 4000), owner);
    } catch (DuplicateKeyException exception) {
      throw new ApiException(HttpStatus.CONFLICT, "CRM_ACCOUNT_ALREADY_EXISTS");
    }
    return jdbc.queryForObject(
        "select * from crm_accounts where company_id = ? and id = ?",
        CrmWorkService::account, companyId, key);
  }

  @Transactional(readOnly = true)
  public Account getAccount(long companyId, long accountId) {
    List<Account> found = jdbc.query(
        "select * from crm_accounts where company_id = ? and id = ?",
        CrmWorkService::account, companyId, accountId);
    if (found.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "CRM_ACCOUNT_NOT_FOUND");
    return found.getFirst();
  }

  @Transactional(readOnly = true)
  public List<AccountContact> accountContactSummaries(long companyId, long accountId) {
    requireAccount(companyId, accountId);
    return jdbc.query("""
        select c.id, c.display_name from crm_account_contacts l
        join contacts c on c.company_id = l.company_id and c.id = l.contact_id
        where l.company_id = ? and l.account_id = ? and c.status = 'ACTIVE'
        order by c.id desc limit 100
        """,
        (rs, row) -> new AccountContact(id(rs, "id"), rs.getString("display_name")),
        companyId, accountId);
  }

  @Transactional
  public Account updateAccount(long companyId, long accountId, long version,
                               String name, String website, String notes, Long owner) {
    String safeName = trimmed(name, 240);
    if (safeName == null || version < 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_ACCOUNT_INVALID_UPDATE");
    }
    if (owner != null) activeMember(companyId, owner);
    int updated;
    try {
      updated = jdbc.update("""
          update crm_accounts
             set name = ?, normalized_name = ?, website = ?, notes = ?,
                 owner_user_id = ?, version = version + 1, updated_at = now()
           where company_id = ? and id = ? and version = ?
          """, safeName, safeName.toLowerCase(java.util.Locale.ROOT),
          trimmed(website, 320), trimmed(notes, 4000), owner,
          companyId, accountId, version);
    } catch (DuplicateKeyException exception) {
      throw new ApiException(HttpStatus.CONFLICT, "CRM_ACCOUNT_ALREADY_EXISTS");
    }
    if (updated == 0) throw new ApiException(HttpStatus.CONFLICT, "CRM_ACCOUNT_VERSION_CONFLICT");
    return jdbc.queryForObject(
        "select * from crm_accounts where company_id = ? and id = ?",
        CrmWorkService::account, companyId, accountId);
  }

  @Transactional
  public void linkContact(long companyId, long accountId, long contactId, long actor) {
    requireAccount(companyId, accountId);
    requireContact(companyId, contactId);
    jdbc.update("""
        insert into crm_account_contacts(company_id, account_id, contact_id, linked_by_user_id)
        values (?, ?, ?, ?) on conflict do nothing
        """, companyId, accountId, contactId, actor);
  }

  @Transactional(readOnly = true)
  public List<String> accountContactIds(long companyId, long accountId) {
    requireAccount(companyId, accountId);
    return jdbc.query(
        "select contact_id from crm_account_contacts where company_id = ? and account_id = ? order by contact_id desc limit 100",
        (rs, row) -> id(rs, "contact_id"), companyId, accountId);
  }

  @Transactional(readOnly = true)
  public Page<Task> tasks(long companyId, Long before, int requestedSize,
                          Long assignee, String status) {
    int size = Math.max(1, Math.min(100, requestedSize));
    if (status != null && !List.of("OPEN", "IN_PROGRESS", "DONE", "CANCELLED").contains(status)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_TASK_STATUS_INVALID");
    }
    if (before != null && before <= 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_INVALID_CURSOR");
    }
    List<Task> results = jdbc.query("""
        select * from crm_tasks
        where company_id = ? and (cast(? as bigint) is null or id < ?)
          and (cast(? as bigint) is null or assignee_user_id = ?)
          and (cast(? as varchar) is null or status = ?)
        order by id desc limit ?
        """, CrmWorkService::task, companyId, before, before, assignee, assignee,
        status, status, size + 1);
    boolean more = results.size() > size;
    List<Task> page = more ? results.subList(0, size) : results;
    return new Page<>(page, more ? page.getLast().id() : null, more);
  }

  @Transactional
  public Task createTask(long companyId, String title, String details, String priority,
                         Instant dueAt, long assignee, Long contactId, Long accountId, long actor) {
    String safeTitle = trimmed(title, 240);
    if (safeTitle == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_TASK_TITLE_REQUIRED");
    if (!List.of("LOW", "NORMAL", "HIGH").contains(priority)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_TASK_PRIORITY_INVALID");
    }
    activeMember(companyId, assignee);
    requireContact(companyId, contactId);
    requireAccount(companyId, accountId);
    long key = IdGenerator.nextId();
    jdbc.update("""
        insert into crm_tasks(id,company_id,title,details,priority,due_at,
                              assignee_user_id,contact_id,account_id,created_by_user_id)
        values(?,?,?,?,?,?,?,?,?,?)
        """, key, companyId, safeTitle, trimmed(details, 4000), priority,
        dueAt == null ? null : Timestamp.from(dueAt),
        assignee, contactId, accountId, actor);
    return getTask(companyId, key);
  }

  @Transactional
  public Task updateTask(long companyId, long taskId, long version, String title,
                         String details, String priority, Instant dueAt, long assignee,
                         Long contactId, Long accountId) {
    String safeTitle = trimmed(title, 240);
    if (safeTitle == null || version < 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_TASK_INVALID_UPDATE");
    }
    if (priority == null || !List.of("LOW", "NORMAL", "HIGH").contains(priority)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_TASK_PRIORITY_INVALID");
    }
    activeMember(companyId, assignee);
    requireContact(companyId, contactId);
    requireAccount(companyId, accountId);
    int updated = jdbc.update("""
        update crm_tasks
           set title = ?, details = ?, priority = ?, due_at = ?,
               assignee_user_id = ?, contact_id = ?, account_id = ?,
               reminder_generation = reminder_generation
                 + case when assignee_user_id <> ?
                   or due_at is distinct from cast(? as timestamptz)
                   then 1 else 0 end,
               version = version + 1, updated_at = now()
         where company_id = ? and id = ? and version = ?
        """, safeTitle, trimmed(details, 4000), priority,
        dueAt == null ? null : Timestamp.from(dueAt),
        assignee, contactId, accountId, assignee,
        dueAt == null ? null : Timestamp.from(dueAt), companyId, taskId, version);
    if (updated == 0) throw new ApiException(HttpStatus.CONFLICT, "CRM_TASK_VERSION_CONFLICT");
    return getTask(companyId, taskId);
  }

  @Transactional(readOnly = true)
  public Task getTask(long companyId, long taskId) {
    List<Task> rows = jdbc.query(
        "select * from crm_tasks where company_id = ? and id = ?",
        CrmWorkService::task, companyId, taskId);
    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "CRM_TASK_NOT_FOUND");
    return rows.getFirst();
  }

  @Transactional
  public Task changeTaskStatus(long companyId, long taskId, long version, String status) {
    if (!List.of("OPEN","IN_PROGRESS","DONE","CANCELLED").contains(status)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRM_TASK_STATUS_INVALID");
    }
    int updated = jdbc.update("""
        update crm_tasks set status = ?, completed_at = case when ? = 'DONE' then now() else null end,
          reminder_generation = reminder_generation + case
            when status in ('DONE', 'CANCELLED') and ? in ('OPEN', 'IN_PROGRESS')
            then 1 else 0 end,
          updated_at = now(), version = version + 1
        where company_id = ? and id = ? and version = ?
        """, status, status, status, companyId, taskId, version);
    if (updated == 0) throw new ApiException(HttpStatus.CONFLICT, "CRM_TASK_VERSION_CONFLICT");
    return getTask(companyId, taskId);
  }
}

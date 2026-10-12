package com.messaging.crm;

import com.messaging.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Called inside the workspace membership transaction. Unfinished assignments move to the
 * active workspace owner; historical closed tasks retain their original assignee for audit.
 */
@Service
@RequiredArgsConstructor
public class CrmTaskMembershipLifecycle {
  private final JdbcTemplate jdbc;

  @Transactional
  public void onUserPurging(long userId) {
    // Company purge owns deleted workspaces. Active/suspended workspaces must never retain
    // an unfinished task for a user who no longer has an accessible account.
    var companies = jdbc.query(
        """
        select distinct t.company_id from crm_tasks t
        join companies c on c.id = t.company_id
        where t.assignee_user_id = ? and t.status in ('OPEN', 'IN_PROGRESS')
          and c.status in ('ACTIVE', 'SUSPENDED')
        order by t.company_id
        """, (rs, row) -> rs.getLong("company_id"), userId);
    for (Long companyId : companies) {
      onMemberDeactivated(companyId, userId);
    }
    // A former customer-company owner is not an active assignment after account purging.
    jdbc.update(
        "update crm_accounts set owner_user_id = null, version = version + 1, updated_at = now() where owner_user_id = ?",
        userId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void onMemberDeactivated(long companyId, long departingUserId) {
    Long remaining =
        jdbc.queryForObject(
            """
            select count(*) from crm_tasks
            where company_id = ? and assignee_user_id = ?
              and status in ('OPEN', 'IN_PROGRESS')
            """, Long.class, companyId, departingUserId);
    if (remaining == null || remaining == 0) {
      jdbc.update(
          """
          update crm_accounts set owner_user_id = null, version = version + 1, updated_at = now()
          where company_id = ? and owner_user_id = ?
          """, companyId, departingUserId);
      return;
    }

    var owners = jdbc.query(
        """
        select m.user_id from company_members m
        join users u on u.id = m.user_id and u.status = 'ACTIVE'
        where m.company_id = ? and m.role = 'OWNER' and m.status = 'ACTIVE'
          and m.user_id <> ?
        order by m.user_id
        limit 1
        for share of m
        """, (rs, row) -> rs.getLong("user_id"), companyId, departingUserId);
    if (owners.isEmpty()) {
      // Fail closed: do not strand active work on an inaccessible assignee.
      throw new ApiException(HttpStatus.CONFLICT, "CRM_REASSIGN_OWNER_UNAVAILABLE");
    }

    jdbc.update(
        """
        update crm_tasks
           set assignee_user_id = ?, version = version + 1,
               reminder_generation = reminder_generation + 1, updated_at = now()
         where company_id = ? and assignee_user_id = ?
           and status in ('OPEN', 'IN_PROGRESS')
        """,
        owners.getFirst(), companyId, departingUserId);

    jdbc.update(
        """
        update crm_accounts set owner_user_id = null, version = version + 1, updated_at = now()
        where company_id = ? and owner_user_id = ?
        """,
        companyId, departingUserId);
  }
}

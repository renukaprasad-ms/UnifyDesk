package com.messaging.crm;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrmReportsService {
  private final JdbcTemplate jdbc;

  public record Count(String label, long value) {}

  public record Value(String currency, String openValue, String wonValue, String lostValue) {}

  public record Trend(String date, long leads, long deals, long completedTasks) {}

  public record Team(String userId, String name, long actions) {}

  public record Report(
      int days,
      List<Count> leads,
      List<Count> deals,
      List<Count> tasks,
      long overdueTasks,
      List<Value> values,
      List<Trend> trend,
      List<Team> team,
      Instant generatedAt) {}

  @Transactional(readOnly = true)
  public Report report(long companyId, int days) {
    if (!List.of(7, 30, 90).contains(days))
      throw new com.messaging.common.exception.ApiException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "CRM_REPORT_PERIOD_INVALID");
    Instant now = Instant.now();
    Timestamp since = Timestamp.from(now.minusSeconds(days * 86400L));
    var leads =
        jdbc.query(
            """
        select l.stage,count(*) n from crm_leads l join contacts c on c.company_id=l.company_id and c.id=l.contact_id
        where l.company_id=? and c.status='ACTIVE' group by l.stage order by l.stage
        """,
            (rs, row) -> new Count(rs.getString("stage"), rs.getLong("n")),
            companyId);
    var deals =
        jdbc.query(
            "select stage,count(*) n from crm_deals where company_id=? group by stage order by stage",
            (rs, row) -> new Count(rs.getString("stage"), rs.getLong("n")),
            companyId);
    var tasks =
        jdbc.query(
            "select status,count(*) n from crm_tasks where company_id=? group by status order by status",
            (rs, row) -> new Count(rs.getString("status"), rs.getLong("n")),
            companyId);
    long overdue =
        jdbc.queryForObject(
            "select count(*) from crm_tasks where company_id=? and status in ('OPEN','IN_PROGRESS') and due_at<?",
            Long.class,
            companyId,
            Timestamp.from(now));
    var values =
        jdbc.query(
            """
        select currency,coalesce(sum(amount) filter(where stage not in ('WON','LOST')),0) open_value,
          coalesce(sum(amount) filter(where stage='WON'),0) won_value,
          coalesce(sum(amount) filter(where stage='LOST'),0) lost_value
        from crm_deals where company_id=? group by currency order by currency
        """,
            (rs, row) ->
                new Value(
                    rs.getString("currency"),
                    rs.getBigDecimal("open_value").stripTrailingZeros().toPlainString(),
                    rs.getBigDecimal("won_value").stripTrailingZeros().toPlainString(),
                    rs.getBigDecimal("lost_value").stripTrailingZeros().toPlainString()),
            companyId);
    var trend =
        jdbc.query(
            """
        with dates as (select generate_series((current_timestamp at time zone 'UTC')::date-(? - 1),
           (current_timestamp at time zone 'UTC')::date,interval '1 day')::date bucket_date),
        leads as (select (l.created_at at time zone 'UTC')::date bucket_date,count(*) n from crm_leads l
          join contacts c on c.company_id=l.company_id and c.id=l.contact_id
          where l.company_id=? and c.status='ACTIVE' and l.created_at>=? group by 1),
        deals as (select (created_at at time zone 'UTC')::date bucket_date,count(*) n from crm_deals where company_id=? and created_at>=? group by 1),
        tasks as (select (occurred_at at time zone 'UTC')::date bucket_date,count(*) n from crm_task_events
          where company_id=? and occurred_at>=? and event_type='STATUS_CHANGED' and new_status='DONE' group by 1)
        select d.bucket_date,coalesce(l.n,0) leads,coalesce(o.n,0) deals,coalesce(t.n,0) tasks
        from dates d left join leads l using(bucket_date) left join deals o using(bucket_date) left join tasks t using(bucket_date) order by d.bucket_date
        """,
            (rs, row) ->
                new Trend(
                    rs.getDate("bucket_date").toString(),
                    rs.getLong("leads"),
                    rs.getLong("deals"),
                    rs.getLong("tasks")),
            days,
            companyId,
            since,
            companyId,
            since,
            companyId,
            since);
    var team =
        jdbc.query(
            """
        select u.id,u.name,count(*) actions from (
          select actor_user_id from crm_lead_stage_events where company_id=? and occurred_at>=?
          union all select actor_user_id from crm_task_events where company_id=? and occurred_at>=?
          union all select actor_user_id from crm_deal_events where company_id=? and occurred_at>=?
          union all select actor_user_id from crm_notes where company_id=? and occurred_at>=?
        ) e join users u on u.id=e.actor_user_id group by u.id,u.name order by actions desc,u.id limit 20
        """,
            (rs, row) -> new Team(rs.getString("id"), rs.getString("name"), rs.getLong("actions")),
            companyId,
            since,
            companyId,
            since,
            companyId,
            since,
            companyId,
            since);
    return new Report(days, leads, deals, tasks, overdue, values, trend, team, now);
  }
}

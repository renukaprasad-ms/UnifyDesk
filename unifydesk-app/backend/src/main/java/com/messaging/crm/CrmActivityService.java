package com.messaging.crm;

import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrmActivityService {
  private final JdbcTemplate jdbc;

  public record Access(
      boolean leads,
      boolean tasks,
      boolean deals,
      boolean email,
      boolean whatsapp,
      boolean broadcasts) {}

  public record Item(
      String id,
      String source,
      String title,
      String detail,
      String actorName,
      String actorUsername,
      String contactId,
      Instant occurredAt,
      String href) {}

  public record Page(List<Item> items, String nextCursor, boolean hasMore) {}

  private record Cursor(Instant at, String source, long id) {}

  private static final String FEED =
      """
      select e.id,'LEAD'::text source,coalesce(c.display_name,'Unnamed lead') title,
        case when e.previous_stage is null then 'Lead created: '||e.new_stage
             else e.previous_stage||' → '||e.new_stage end detail,
        e.actor_user_id::text actor_id,e.contact_id,e.occurred_at,'/crm/leads?contactId='||e.contact_id href
      from crm_lead_stage_events e join contacts c on c.company_id=e.company_id and c.id=e.contact_id
      where e.company_id=:company and :leads and c.status='ACTIVE'
      union all
      select e.id,'TASK',t.title,
        case when e.event_type in ('STATUS_CHANGED','STEP_CHANGED') then coalesce(e.previous_column,e.previous_status)||' → '||coalesce(e.new_column,e.new_status)
          when e.event_type='CREATED' then 'Task created' else 'Updated: '||e.changed_fields end,
        e.actor_user_id::text,t.contact_id,e.occurred_at,'/crm/tasks?taskId='||t.id
      from crm_task_events e join crm_tasks t on t.company_id=e.company_id and t.id=e.task_id
      where e.company_id=:company and :tasks
      union all
      select e.id,'DEAL',d.title,
        case when e.event_type='STAGE_CHANGED' then e.previous_stage||' → '||e.new_stage
          when e.event_type='CREATED' then 'Deal created' else 'Updated: '||e.changed_fields end,
        e.actor_user_id::text,d.contact_id,e.occurred_at,'/crm/deals?dealId='||d.id
      from crm_deal_events e join crm_deals d on d.company_id=e.company_id and d.id=e.deal_id
      where e.company_id=:company and :deals
      union all
      select n.id,'NOTE','Internal note',n.body,n.actor_user_id::text,n.contact_id,n.occurred_at,
        case when n.contact_id is null then null else '/contacts/'||n.contact_id end
      from crm_notes n where n.company_id=:company
      union all
      select e.id,'CONTACT',coalesce(c.display_name,'Contact'),replace(e.event_type,'_',' '),
        case when e.actor_type='USER' then e.actor_id else null end,e.contact_id,e.occurred_at,'/contacts/'||e.contact_id
      from contact_activity_events e join contacts c on c.company_id=e.company_id and c.id=e.contact_id
      where e.company_id=:company and c.status='ACTIVE'
      union all
      select c.id,'INBOX',case when c.provider_type='META_WHATSAPP' then 'WhatsApp conversation' else 'Email conversation' end,
        'Conversation activity',null,
        case when cast(:contact as bigint) is null then null else cast(:contact as bigint) end,
        coalesce(c.last_message_at,c.updated_at,c.created_at),
        case when c.provider_type='META_WHATSAPP' then '/inbox/social-media' else '/inbox/email' end||'?conversationId='||c.id
      from inbox_conversations c where c.company_id=:company
        and ((c.provider_type='META_WHATSAPP' and :whatsapp) or (c.provider_type<>'META_WHATSAPP' and :email))
        and (cast(:contact as bigint) is null or exists(select 1 from inbox_participant_contact_links l
          where l.company_id=c.company_id and l.conversation_id=c.id and l.contact_id=:contact and l.resolution='MATCHED'))
      union all
      select r.id,'BROADCAST',m.channel_type||' broadcast','Recipient status: '||r.status,null,r.contact_id,
        coalesce(r.accepted_at,r.bounced_at,r.failed_at,r.updated_at,r.created_at),
        case when m.channel_type='WHATSAPP' then '/broadcast/social-media' when m.channel_type='SMS' then '/broadcast/sms'
             else '/broadcast/email' end||'?broadcastId='||m.id
      from broadcast_recipients r join broadcast_runs run on run.company_id=r.company_id and run.id=r.run_id
      join broadcast_messages m on m.company_id=run.company_id and m.id=run.broadcast_message_id
      where r.company_id=:company and :broadcasts
      """;

  @Transactional(readOnly = true)
  public Page page(
      long companyId,
      Access access,
      String source,
      String person,
      Long contactId,
      Instant from,
      Instant to,
      String cursor,
      int size) {
    if (source != null
        && !List.of("LEAD", "TASK", "DEAL", "NOTE", "CONTACT", "INBOX", "BROADCAST")
            .contains(source)) throw invalid();
    if (person != null && person.length() > 100) throw invalid();
    if (contactId != null && contactId <= 0) throw invalid();
    Instant end = to == null ? Instant.now() : to;
    Instant start = from == null ? end.minusSeconds(30L * 86400) : from;
    if (!start.isBefore(end) || end.getEpochSecond() - start.getEpochSecond() > 366L * 86400)
      throw invalid();
    Cursor before = decode(cursor, end);
    Map<String, Object> params = new HashMap<>();
    params.put("company", companyId);
    params.put("leads", access.leads());
    params.put("tasks", access.tasks());
    params.put("deals", access.deals());
    params.put("email", access.email());
    params.put("whatsapp", access.whatsapp());
    params.put("broadcasts", access.broadcasts());
    params.put("contact", contactId);
    params.put("source", source);
    params.put("person", person == null ? "" : person.trim());
    params.put("start", Timestamp.from(start));
    params.put("end", Timestamp.from(end));
    params.put("before", Timestamp.from(before.at()));
    params.put("beforeSource", before.source());
    params.put("beforeId", before.id());
    int limit = Math.max(1, Math.min(size, 100));
    params.put("limit", limit + 1);
    var rows =
        new NamedParameterJdbcTemplate(jdbc)
            .query(
                """
        select x.*,u.name actor_name,u.username actor_username from (
        """
                    + FEED
                    + """
        ) x left join users u on u.id::text=x.actor_id
        where x.occurred_at>=:start and x.occurred_at<:end
          and (x.occurred_at,x.source,x.id)<(:before,:beforeSource,:beforeId)
          and (cast(:source as text) is null or x.source=:source)
          and (cast(:contact as bigint) is null or x.contact_id=:contact)
          and position(lower(:person) in lower(coalesce(u.name,'')||' '||coalesce(u.username,'')))>0
        order by x.occurred_at desc,x.source desc,x.id desc limit :limit
        """,
                params,
                (rs, row) ->
                    new Item(
                        rs.getString("id"),
                        rs.getString("source"),
                        rs.getString("title"),
                        rs.getString("detail"),
                        rs.getString("actor_name"),
                        rs.getString("actor_username"),
                        rs.getString("contact_id"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("href")));
    boolean more = rows.size() > limit;
    var items = more ? rows.subList(0, limit) : rows;
    return new Page(items, more ? encode(items.getLast()) : null, more);
  }

  @Transactional
  public void note(long companyId, Long contactId, String body, long actor) {
    if (body == null || body.isBlank() || body.trim().length() > 2000) throw invalid();
    if (contactId != null
        && jdbc.queryForObject(
                "select count(*) from contacts where company_id=? and id=? and status='ACTIVE'",
                Long.class,
                companyId,
                contactId)
            != 1) throw invalid();
    jdbc.update(
        "insert into crm_notes(id,company_id,contact_id,body,actor_user_id) values(?,?,?,?,?)",
        IdGenerator.nextId(),
        companyId,
        contactId,
        body.trim(),
        actor);
  }

  private static String encode(Item item) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(
            (item.occurredAt() + "|" + item.source() + "|" + item.id())
                .getBytes(StandardCharsets.UTF_8));
  }

  private static Cursor decode(String value, Instant end) {
    if (value == null || value.isBlank()) return new Cursor(end, "ZZZ", Long.MAX_VALUE);
    try {
      if (value.length() > 200) throw invalid();
      String[] parts =
          new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
      if (parts.length != 3 || !parts[1].matches("[A-Z]{1,20}")) throw invalid();
      long id = Long.parseLong(parts[2]);
      if (id <= 0) throw invalid();
      return new Cursor(Instant.parse(parts[0]), parts[1], id);
    } catch (RuntimeException e) {
      throw invalid();
    }
  }

  private static ApiException invalid() {
    return new ApiException(HttpStatus.BAD_REQUEST, "CRM_ACTIVITY_INVALID_INPUT");
  }
}

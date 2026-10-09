package com.messaging.billing.controller;

import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.response.ApiResponse;
import com.messaging.security.service.CurrentUserService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational message activity—not financial charge units. Counts rows from distinct
 * channel message identity stores instead of provider webhook events to avoid retry inflation.
 * Coverage: connected inbox email, legacy managed email/SMS broadcast and Meta WhatsApp.
 * Includes explicitly linked and ingested standalone Mail product handoffs; unlinked
 * Mail projects are excluded. A source may appear in multiple stores until deduplicated.
 */
@RestController
@RequestMapping("/api/v1/admin/billing/analytics")
@RequiredArgsConstructor
public class BillingChannelTrafficController {
  private final JdbcTemplate jdbcTemplate;
  private final PlatformAdminAuthorizationService authorization;
  private final CurrentUserService currentUserService;

  public record TrafficPoint(
      String companyId, String day, String channel, String direction, long messages) {}

  public record TrafficReport(Instant from, Instant to, List<TrafficPoint> points) {}

  @GetMapping("/channel-traffic")
  public ResponseEntity<ApiResponse<TrafficReport>> channelTraffic(
      @RequestParam Instant from, @RequestParam Instant to) {
    authorization.requireSuperAdmin(currentUserService.currentUserId());
    if (!from.isBefore(to) || to.isAfter(Instant.now().plusSeconds(60))
        || from.isBefore(to.minusSeconds(93L * 86400))) {
      throw new BadRequestException("Channel traffic reporting is limited to 93 days");
    }
    Timestamp start = Timestamp.from(from);
    Timestamp end = Timestamp.from(to);
    List<TrafficPoint> rows = jdbcTemplate.query(
        """
        with observed as (
          select c.company_id, 'EMAIL' as channel, m.direction as direction,
                 coalesce(m.sent_at, m.created_at) as occurred_at
            from inbox_messages m
            join channel_connections c on c.id = m.connection_id
           where c.channel_type = 'EMAIL' and m.status in ('RECEIVED', 'SENT')
             and coalesce(m.sent_at, m.created_at) >= ?
             and coalesce(m.sent_at, m.created_at) < ?
          union all
          select d.company_id, 'EMAIL', 'OUTBOUND', d.sent_event_at
            from managed_email_recipient_delivery d
           where d.sent_event_at >= ? and d.sent_event_at < ?
          union all
          select d.company_id, 'SMS', 'OUTBOUND', d.accepted_at
            from managed_sms_recipient_delivery d
           where d.accepted_at >= ? and d.accepted_at < ?
          union all
          select m.company_id, 'EMAIL', 'OUTBOUND', m.occurred_at
            from billing_mail_usage_imports m
           where m.occurred_at >= ? and m.occurred_at < ?
          union all
          select w.company_id, 'WHATSAPP', w.direction, w.created_at
            from meta_whatsapp_message_runtime w
           where w.detailed_status in ('ACCEPTED', 'SENT', 'DELIVERED', 'READ')
             and w.created_at >= ? and w.created_at < ?
        )
        select company_id, channel, direction,
               to_char((occurred_at at time zone 'UTC')::date, 'YYYY-MM-DD') as day,
               count(*) as message_count
          from observed
         group by company_id, channel, direction, (occurred_at at time zone 'UTC')::date
         order by day, company_id, channel, direction
        """,
        (rs, row) -> new TrafficPoint(
            Long.toString(rs.getLong("company_id")), rs.getString("day"),
            rs.getString("channel"), rs.getString("direction"),
            rs.getLong("message_count")),
        start, end, start, end, start, end, start, end, start, end);
    return ResponseEntity.ok(ApiResponse.success(
        HttpStatus.OK.value(), new TrafficReport(from, to, rows), "Channel traffic fetched"));
  }
}

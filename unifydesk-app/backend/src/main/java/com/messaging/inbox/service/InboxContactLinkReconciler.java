package com.messaging.inbox.service;

import com.messaging.inbox.enums.InboxParticipantType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InboxContactLinkReconciler {

  private static final int BATCH_SIZE = 100;

  private final JdbcTemplate jdbcTemplate;
  private final InboxParticipantContactLinkService linkService;

  @Scheduled(fixedDelayString = "${app.inbox.contact-link-reconcile-ms:60000}")
  public void reconcile() {
    List<Candidate> candidates =
        jdbcTemplate.query(
            """
            select c.company_id,p.conversation_id,p.participant_type,p.address
            from inbox_participants p
            join inbox_conversations c on c.id=p.conversation_id
            left join inbox_participant_contact_links l
              on l.company_id=c.company_id
             and l.conversation_id=p.conversation_id
             and l.identity_type=case
                   when p.participant_type='EMAIL_ADDRESS' then 'EMAIL'
                   when p.participant_type='PHONE_NUMBER' then 'PHONE'
                   else 'UNKNOWN'
                 end
             and l.participant_address=case
                   when p.participant_type='EMAIL_ADDRESS' then lower(trim(p.address))
                   else trim(p.address)
                 end
            where p.participant_type in ('EMAIL_ADDRESS','PHONE_NUMBER')
              and (
                l.id is null
                or (l.resolution='UNMATCHED' and l.resolved_at < now() - interval '1 hour')
              )
            order by case when l.id is null then 0 else 1 end,
                     coalesce(l.resolved_at,p.created_at),
                     p.id
            limit ?
            """,
            (rs, rowNum) ->
                new Candidate(
                    rs.getLong("company_id"),
                    rs.getLong("conversation_id"),
                    InboxParticipantType.valueOf(rs.getString("participant_type")),
                    rs.getString("address")),
            BATCH_SIZE);

    for (Candidate candidate : candidates) {
      linkService.resolve(
          candidate.companyId(),
          candidate.conversationId(),
          candidate.participantType(),
          candidate.address());
    }
  }

  private record Candidate(
      long companyId,
      long conversationId,
      InboxParticipantType participantType,
      String address) {}
}

package com.messaging.crm;

import com.messaging.contacts.core.service.ContactMergeExtension;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reconciles CRM relationships in the canonical Contacts merge transaction.
 *
 * <p>The target Contact is authoritative when both contacts already have lead stages. Otherwise the
 * source lead is transferred. Source stage history is retained under the target, while linked
 * customer companies are deduplicated and follow-up tasks are reassigned. This extension does not
 * modify contact identities, consent, or messaging/suppression records.
 */
@Component
@RequiredArgsConstructor
public class CrmContactMergeExtension implements ContactMergeExtension {
  private final JdbcTemplate jdbc;

  @Override
  public void reconcile(
      long companyId, long sourceContactId, long targetContactId, long actorUserId) {
    // ContactService locks both canonical contacts and wraps every extension in one transaction.
    // Insert target lead before reparenting events due to the composite stage-events FK.
    jdbc.update(
        """
        insert into crm_leads
          (company_id, contact_id, stage, version, created_by_user_id, updated_by_user_id,
           created_at, updated_at)
        select company_id, ?, stage, version, created_by_user_id, ?, created_at, now()
        from crm_leads
        where company_id = ? and contact_id = ?
        on conflict (company_id, contact_id) do nothing
        """,
        targetContactId,
        actorUserId,
        companyId,
        sourceContactId);

    jdbc.update(
        """
        update crm_lead_stage_events set contact_id = ?
        where company_id = ? and contact_id = ?
        """,
        targetContactId,
        companyId,
        sourceContactId);

    jdbc.update(
        """
        delete from crm_leads where company_id = ? and contact_id = ?
        """,
        companyId,
        sourceContactId);

    jdbc.update(
        """
        insert into crm_account_contacts
          (company_id, account_id, contact_id, linked_at, linked_by_user_id)
        select company_id, account_id, ?, linked_at, linked_by_user_id
        from crm_account_contacts
        where company_id = ? and contact_id = ?
        on conflict (company_id, account_id, contact_id) do nothing
        """,
        targetContactId,
        companyId,
        sourceContactId);

    jdbc.update(
        """
        delete from crm_account_contacts where company_id = ? and contact_id = ?
        """,
        companyId,
        sourceContactId);

    jdbc.update(
        """
        update crm_tasks
        set contact_id = ?, version = version + 1, updated_at = now()
        where company_id = ? and contact_id = ?
        """,
        targetContactId,
        companyId,
        sourceContactId);
    jdbc.update(
        "update crm_deals set contact_id=?,version=version+1,updated_at=now() where company_id=? and contact_id=?",
        targetContactId,
        companyId,
        sourceContactId);
    jdbc.update(
        "update crm_notes set contact_id=? where company_id=? and contact_id=?",
        targetContactId,
        companyId,
        sourceContactId);
  }
}

package com.messaging.admin.service;

import com.messaging.common.exception.BadRequestException;
import com.messaging.security.service.CurrentUserService;
import com.messaging.managedsms.control.service.ManagedSmsEligibilityService;
import com.messaging.managedsms.metareach.MetaReachProperties;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class PlatformMetaReachCompanyApprovalService {
  private final PlatformAdminAuthorizationService authorization;
  private final CurrentUserService currentUser;
  private final JdbcTemplate jdbc;
  private final MetaReachProperties props;
  private final ManagedSmsEligibilityService eligibility;

  public record Approval(String companyId, String peId, String evidenceReference,
      boolean active,Instant approvedAt) {}

  @Transactional(readOnly=true)
  public Approval get(Long companyId) {
    authorization.requireSuperAdmin(currentUser.currentUserId());
    List<Approval> rows=jdbc.query("""
      select company_id,pe_id,evidence_reference,active,approved_at
      from managed_sms_metareach_company_approvals where company_id=?
      """,(rs,i)->new Approval(Long.toString(rs.getLong(1)),rs.getString(2),
        rs.getString(3),rs.getBoolean(4),rs.getTimestamp(5).toInstant()),companyId);
    return rows.isEmpty()?null:rows.getFirst();
  }

  @Transactional
  public Approval set(Long companyId,String peId,String evidence,String reason,boolean active) {
    Long actor=currentUser.currentUserId();
    authorization.requireSuperAdmin(actor);
    if (reason==null || reason.trim().length()<8 || reason.length()>500)
      throw new BadRequestException("A detailed review reason is required");
    if (!active) {
      jdbc.update("""
          update managed_sms_metareach_company_approvals
          set active=false,reviewed_reason=? where company_id=?
          """,reason.trim(),companyId);
      eligibility.recalculate(companyId);
      return get(companyId);
    }
    if (!props.ready()) throw new BadRequestException("MetaReach contract is not configured");
    if (peId==null || peId.isBlank() || evidence==null || evidence.isBlank()
        || evidence.length()>255)
      throw new BadRequestException("Verified PE ID and MetaReach account evidence are required");
    Integer verified=jdbc.queryForObject("""
        select count(*) from managed_sms_dlt_profiles
        where company_id=? and pe_id=? and status='ACTIVE' and tm_chain_status='ACTIVE'
          and archived_at is null and stale_after>now()
        """,Integer.class,companyId,peId.trim());
    if (verified==null || verified<1)
      throw new BadRequestException("DLT PE and PE-TM chain are not currently verified");
    jdbc.update("""
        insert into managed_sms_metareach_company_approvals
          (company_id,pe_id,evidence_reference,approved_by,approved_at,active,reviewed_reason)
        values (?,?,?,?,now(),true,?)
        on conflict(company_id) do update set pe_id=excluded.pe_id,
            evidence_reference=excluded.evidence_reference,approved_by=excluded.approved_by,
            approved_at=excluded.approved_at,active=true,reviewed_reason=excluded.reviewed_reason
        """,companyId,peId.trim(),evidence.trim(),actor,reason.trim());
    jdbc.update("""
        update managed_sms_tenants set last_provider_sync_at=now(),
          provider_account_ref=?,updated_at=now() where company_id=?
        """,evidence.trim(),companyId);
    eligibility.recalculate(companyId);
    return get(companyId);
  }
}

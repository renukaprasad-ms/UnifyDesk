package com.messaging.admin.service;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.id.IdGenerator;
import com.messaging.security.service.CurrentUserService;
import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformManagedProviderService {
  private final PlatformAdminAuthorizationService authorization;
  private final CurrentUserService currentUser;
  private final JdbcTemplate jdbc;

  private final com.messaging.managedsms.metareach.MetaReachProperties metaReach;

  public record ProviderOption(String code, String name, boolean available, String note) {}
  public record Route(String channel, String provider, long revision, List<ProviderOption> options) {}

  @Transactional(readOnly = true)
  public List<Route> all() {
    authorization.requireSuperAdmin(currentUser.currentUserId());
    return List.of(get("EMAIL"), get("SMS"));
  }

  @Transactional
  public Route change(String channel, String provider, long expectedRevision, String reason) {
    long actor = currentUser.currentUserId();
    authorization.requireSuperAdmin(actor);
    if (!"EMAIL".equals(channel) && !"SMS".equals(channel)) {
      throw new BadRequestException("Unsupported managed provider channel");
    }
    if (reason == null || reason.trim().length() < 8 || reason.length() > 500) {
      throw new BadRequestException("A change reason of 8 to 500 characters is required");
    }
    if (!("EMAIL".equals(channel) && "AMAZON_SES".equals(provider))
        && !("SMS".equals(channel) && ("MSG91_SMS".equals(provider) || "METAREACH_SMS".equals(provider)))) {
      throw new BadRequestException("Unsupported or not-yet-implemented managed provider");
    }
    if ("METAREACH_SMS".equals(provider) && !metareachReady()) {
      throw new BadRequestException("MetaReach is not enabled or configured on the backend");
    }

    Current existing = jdbc.queryForObject(
        "select provider_code, revision from platform_managed_provider_routes where channel=? for update",
        (rs,i) -> new Current(rs.getString(1),rs.getLong(2)),channel);
    if (existing == null || existing.revision() != expectedRevision) {
      throw new ConflictException("Provider routing was updated; refresh before saving");
    }
    if (existing.provider().equals(provider)) return get(channel);

    jdbc.update(
        "update platform_managed_provider_routes set provider_code=?,revision=revision+1,updated_by=?,updated_at=now() where channel=?",
        provider,actor,channel);
    jdbc.update(
        "insert into platform_managed_provider_route_audit(id,channel,previous_provider,new_provider,previous_revision,new_revision,actor_user_id,reason) values (?,?,?,?,?,?,?,?)",
        IdGenerator.nextId(),channel,existing.provider(),provider,existing.revision(),existing.revision()+1,actor,reason.trim());
    return get(channel);
  }

  @Transactional(readOnly = true)
  public String smsProviderForNewRun() {
    return jdbc.queryForObject(
        "select provider_code from platform_managed_provider_routes where channel='SMS'",
        String.class);
  }

  @Transactional(readOnly = true)
  public String smsProviderForCompany(Long companyId) {
    if (companyId == null) throw new BadRequestException("Company is required");
    List<String> override=jdbc.queryForList(
      "select provider_code from platform_managed_provider_company_routes where channel='SMS' and company_id=?",
      String.class,companyId);
    return override.isEmpty() ? smsProviderForNewRun() : override.getFirst();
  }

  @Transactional(readOnly = true)
  public String companyOverride(Long companyId) {
    authorization.requireSuperAdmin(currentUser.currentUserId());
    List<String> override=jdbc.queryForList(
      "select provider_code from platform_managed_provider_company_routes where channel='SMS' and company_id=?",
      String.class,companyId);
    return override.isEmpty() ? null : override.getFirst();
  }

  @Transactional
  public String setCompanyOverride(Long companyId,String provider,String reason) {
    Long actor=currentUser.currentUserId();
    authorization.requireSuperAdmin(actor);
    if (reason==null || reason.trim().length()<8 || reason.length()>500)
      throw new BadRequestException("A change reason of 8 to 500 characters is required");
    if (provider!=null && !provider.equals("MSG91_SMS") && !provider.equals("METAREACH_SMS"))
      throw new BadRequestException("Unsupported company SMS provider");
    if ("METAREACH_SMS".equals(provider)) {
      if (!metareachReady()) throw new BadRequestException("MetaReach is not configured");
      Integer approved=jdbc.queryForObject("""
          select count(*) from managed_sms_metareach_company_approvals
          where company_id=? and active=true
          """,Integer.class,companyId);
      if (!Integer.valueOf(1).equals(approved))
        throw new BadRequestException("Company MetaReach PE/TM mapping is not approved");
    }
    String previous=smsProviderForCompany(companyId);
    if (provider==null) {
      jdbc.update("delete from platform_managed_provider_company_routes where company_id=? and channel='SMS'",
          companyId);
    } else {
      jdbc.update("""
          insert into platform_managed_provider_company_routes(
              company_id,channel,provider_code,revision,changed_by,reason)
          values(?,'SMS',?,1,?,?)
          on conflict(company_id,channel) do update set
              provider_code=excluded.provider_code,revision=platform_managed_provider_company_routes.revision+1,
              changed_by=excluded.changed_by,reason=excluded.reason,changed_at=now()
          """,companyId,provider,actor,reason.trim());
    }
    jdbc.update("""
        insert into platform_managed_sms_company_route_events(
          id,company_id,previous_provider,new_provider,changed_by,reason)
        values(?,?,?,?,?,?)
        """, com.messaging.common.id.IdGenerator.nextId(),companyId,previous,
          smsProviderForCompany(companyId),actor,reason.trim());
    return smsProviderForCompany(companyId);
  }

  private Route get(String channel) {
    Current current = jdbc.queryForObject(
        "select provider_code, revision from platform_managed_provider_routes where channel=?",
        (rs,i) -> new Current(rs.getString(1),rs.getLong(2)),channel);
    if (current == null) throw new IllegalStateException("Missing platform provider route");
    List<ProviderOption> options = "SMS".equals(channel)
        ? List.of(
            new ProviderOption("MSG91_SMS","MSG91",true,"Existing managed SMS provider"),
            new ProviderOption("METAREACH_SMS","MetaReach",metareachReady(),
                metareachReady()?"Polling-based delivery reports":"Requires MetaReach environment credentials and feature flag"))
        : List.of(
            new ProviderOption("AMAZON_SES","Amazon SES",true,"Current managed email provider"),
            new ProviderOption("UNIFYDESK_EMAIL","UnifyDesk Email",false,"Future provider; not implemented"));
    return new Route(channel,current.provider(),current.revision(),options);
  }

  private boolean metareachReady() {
    return metaReach.ready();
  }
  private record Current(String provider,long revision) {}
}

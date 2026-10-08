package com.messaging.managedsms.runtime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcManagedSmsRuntimeRepository implements ManagedSmsRuntimeRepository {

  private static final String BASE_TEMPLATE_SQL =
      """
      select s.company_id,
             s.id as sender_id,
             c.id as connection_id,
             t.id as tenant_id,
             t.created_at as tenant_created_at,
             t.status as tenant_status,
             t.sending_enabled as tenant_sending_enabled,
             t.last_provider_sync_at as tenant_last_provider_sync_at,
             p.id as dlt_profile_id,
             p.pe_id,
             p.status as profile_status,
             p.tm_chain_status,
             p.checked_at as profile_checked_at,
             p.stale_after as profile_stale_after,
             h.id as header_id,
             h.header,
             h.message_category,
             h.status as header_status,
             h.provider_mapping_status as header_provider_mapping_status,
             h.checked_at as header_checked_at,
             h.stale_after as header_stale_after,
             mt.id as template_id,
             v.id as template_version_id,
             v.version_no,
             v.dlt_template_id,
             v.body_text as approved_body,
             v.body_fingerprint,
             v.variables_schema_json,
             v.cta_policy_json,
             v.ruleset_version,
             v.dlt_status,
             v.status as template_version_status,
             v.checked_at as template_checked_at,
             v.stale_after as template_stale_after,
             pb.id as provider_binding_id,
             t.provider_mode,
             case
               when t.provider_mode = 'SHARED_MASTER' then 'master'
               else coalesce(t.provider_client_ref, t.provider_account_ref, '')
             end as provider_account_key,
             pb.provider_template_ref,
             case when (select provider_code from platform_managed_provider_routes where channel='SMS')
                         = 'METAREACH_SMS'
                  then pb.provider_template_ref else pb.provider_flow_ref
             end as provider_flow_ref,
             pb.status as provider_binding_status,
             pb.last_sync_at as provider_binding_last_sync_at,
             c.status as connection_status,
             c.sending_enabled as connection_sending_enabled,
             c.management_mode,
             s.status as sender_status
        from broadcast_senders s
        join broadcast_sender_connections c
          on c.id = s.connection_id and c.company_id = s.company_id
        join managed_sms_sender_bindings sb
          on sb.sender_id = s.id and sb.company_id = s.company_id
        join managed_sms_tenants t
          on t.id = sb.tenant_id and t.company_id = sb.company_id
        join managed_sms_headers h
          on h.id = sb.header_id and h.company_id = sb.company_id
        join managed_sms_dlt_profiles p
          on p.id = h.dlt_profile_id and p.company_id = h.company_id
        join managed_sms_templates mt
          on mt.company_id = s.company_id
        join managed_sms_template_versions v
          on v.template_id = mt.id and v.company_id = mt.company_id
        join managed_sms_template_headers th
          on th.template_version_id = v.id
         and th.header_id = h.id
         and th.company_id = s.company_id
        join managed_sms_provider_bindings pb
          on pb.template_version_id = v.id
         and pb.header_id = h.id
         and pb.company_id = s.company_id
         and pb.tenant_id = t.id
         and pb.provider = 'MSG91_SMS'
       where s.company_id = ?
         and s.id = ?
         and mt.id = ?
         and c.provider = 'MSG91_SMS'
         and c.management_mode = 'MESSAGING_MANAGED'
         and p.archived_at is null
         and h.archived_at is null
         and mt.archived_at is null
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  @Transactional(readOnly = true)
  public Optional<ManagedTemplateRuntime> findTemplateForCompose(
      Long companyId, Long senderId, Long templateId) {
    String sql =
        BASE_TEMPLATE_SQL
            + """
             and v.version_no = (
               select max(v2.version_no)
                 from managed_sms_template_versions v2
                where v2.template_id = mt.id
                  and v2.company_id = mt.company_id
                  and v2.status = 'ACTIVE'
             )
            order by v.version_no desc
            limit 1
            """;
    List<ManagedTemplateRuntime> rows =
        jdbcTemplate.query(sql, (rs, rowNum) -> templateRuntime(rs), companyId, senderId, templateId);
    return rows.stream().findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ManagedTemplateRuntime> findTemplateForLaunch(
      Long companyId, Long senderId, Long templateId, Long versionNo) {
    String sql = BASE_TEMPLATE_SQL + " and v.version_no = ? limit 1";
    List<ManagedTemplateRuntime> rows =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) -> templateRuntime(rs),
            companyId,
            senderId,
            templateId,
            versionNo);
    return rows.stream().findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DeliveryRuntime> findDeliveryRuntime(
      Long companyId, Long runId, Long senderId) {
    List<DeliveryRuntime> rows =
        jdbcTemplate.query(
            """
            select rb.company_id,
                   rb.run_id,
                   run.sender_id,
                   s.connection_id,
                   rb.tenant_id,
                   t.created_at as tenant_created_at,
                   t.status as tenant_status,
                   t.sending_enabled as tenant_sending_enabled,
                   t.last_provider_sync_at as tenant_last_provider_sync_at,
                   rb.dlt_profile_id,
                   rb.pe_id_snapshot,
                   p.status as profile_status,
                   p.tm_chain_status,
                   p.checked_at as profile_checked_at,
                   p.stale_after as profile_stale_after,
                   rb.header_id,
                   rb.header_snapshot,
                   h.message_category,
                   h.status as header_status,
                   h.checked_at as header_checked_at,
                   h.stale_after as header_stale_after,
                   rb.template_id,
                   rb.template_version_id,
                   rb.dlt_template_id_snapshot,
                   rb.approved_body_snapshot,
                   rb.body_fingerprint_snapshot,
                   rb.variables_schema_snapshot,
                   rb.cta_policy_snapshot,
                   v.status as template_version_status,
                   v.checked_at as template_checked_at,
                   v.stale_after as template_stale_after,
                   rb.provider_binding_id,
                   t.provider_mode,
                   case
                     when t.provider_mode = 'SHARED_MASTER' then 'master'
                     else coalesce(rb.provider_client_ref_snapshot, '')
                   end as provider_account_key,
                   rb.provider_template_ref_snapshot,
                   rb.provider_flow_ref_snapshot,
                   rb.ruleset_version_snapshot,
                   pb.status as provider_binding_status,
                   pb.last_sync_at as provider_binding_last_sync_at,
                   c.status as connection_status,
                   c.sending_enabled as connection_sending_enabled,
                   c.management_mode,
                   s.status as sender_status
              from managed_sms_run_bindings rb
              join broadcast_runs run
                on run.id = rb.run_id and run.company_id = rb.company_id
              join broadcast_senders s
                on s.id = run.sender_id and s.company_id = run.company_id
              join broadcast_sender_connections c
                on c.id = s.connection_id and c.company_id = s.company_id
              join managed_sms_tenants t
                on t.id = rb.tenant_id and t.company_id = rb.company_id
              join managed_sms_dlt_profiles p
                on p.id = rb.dlt_profile_id and p.company_id = rb.company_id
              join managed_sms_headers h
                on h.id = rb.header_id and h.company_id = rb.company_id
              join managed_sms_template_versions v
                on v.id = rb.template_version_id and v.company_id = rb.company_id
              join managed_sms_provider_bindings pb
                on pb.id = rb.provider_binding_id and pb.company_id = rb.company_id
             where rb.company_id = ?
               and rb.run_id = ?
               and run.sender_id = ?
               and c.provider = 'MSG91_SMS'
               and c.management_mode = 'MESSAGING_MANAGED'
             limit 1
            """,
            (rs, rowNum) -> deliveryRuntime(rs),
            companyId,
            runId,
            senderId);
    return rows.stream().findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CorrelationRuntime> findCorrelation(String correlationToken) {
    List<CorrelationRuntime> rows =
        jdbcTemplate.query(
            """
            select c.correlation_token,
                   c.company_id,
                   c.run_id,
                   c.recipient_id,
                   c.attempt_no,
                   c.provider_request_id,
                   case
                     when rb.delivery_provider='METAREACH_SMS' then 'metareach:master'
                     when t.provider_mode='SHARED_MASTER' then 'master'
                     else coalesce(nullif(rb.provider_client_ref_snapshot, ''), '')
                   end as provider_account_key
              from managed_sms_correlations c
              join managed_sms_run_bindings rb
                on rb.run_id = c.run_id and rb.company_id = c.company_id
              join managed_sms_tenants t
                on t.id=rb.tenant_id and t.company_id=rb.company_id
              join broadcast_recipients r
                on r.id = c.recipient_id
               and r.company_id = c.company_id
               and r.run_id = c.run_id
             where c.correlation_token = ?
               and c.expires_at > now()
             limit 1
            """,
            (rs, rowNum) ->
                new CorrelationRuntime(
                    rs.getString("correlation_token"),
                    rs.getLong("company_id"),
                    rs.getLong("run_id"),
                    rs.getLong("recipient_id"),
                    rs.getInt("attempt_no"),
                    rs.getString("provider_request_id"),
                    rs.getString("provider_account_key")),
            correlationToken);
    return rows.stream().findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean tenantSendingEnabled(Long companyId) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
              from managed_sms_tenants
             where company_id = ?
               and status = 'READY'
               and sending_enabled = true
            """,
            Integer.class,
            companyId);
    return count != null && count > 0;
  }

  private ManagedTemplateRuntime templateRuntime(ResultSet rs) throws SQLException {
    return new ManagedTemplateRuntime(
        rs.getLong("company_id"),
        rs.getLong("sender_id"),
        rs.getLong("connection_id"),
        rs.getLong("tenant_id"),
        instant(rs, "tenant_created_at"),
        rs.getString("tenant_status"),
        rs.getBoolean("tenant_sending_enabled"),
        instant(rs, "tenant_last_provider_sync_at"),
        rs.getLong("dlt_profile_id"),
        rs.getString("pe_id"),
        rs.getString("profile_status"),
        rs.getString("tm_chain_status"),
        instant(rs, "profile_checked_at"),
        instant(rs, "profile_stale_after"),
        rs.getLong("header_id"),
        rs.getString("header"),
        rs.getString("message_category"),
        rs.getString("header_status"),
        rs.getString("header_provider_mapping_status"),
        instant(rs, "header_checked_at"),
        instant(rs, "header_stale_after"),
        rs.getLong("template_id"),
        rs.getLong("template_version_id"),
        rs.getLong("version_no"),
        rs.getString("dlt_template_id"),
        rs.getString("approved_body"),
        rs.getString("body_fingerprint"),
        rs.getString("variables_schema_json"),
        rs.getString("cta_policy_json"),
        rs.getString("ruleset_version"),
        rs.getString("dlt_status"),
        rs.getString("template_version_status"),
        instant(rs, "template_checked_at"),
        instant(rs, "template_stale_after"),
        rs.getLong("provider_binding_id"),
        rs.getString("provider_mode"),
        rs.getString("provider_account_key"),
        rs.getString("provider_template_ref"),
        rs.getString("provider_flow_ref"),
        rs.getString("provider_binding_status"),
        instant(rs, "provider_binding_last_sync_at"),
        rs.getString("connection_status"),
        rs.getBoolean("connection_sending_enabled"),
        rs.getString("management_mode"),
        rs.getString("sender_status"));
  }

  private DeliveryRuntime deliveryRuntime(ResultSet rs) throws SQLException {
    return new DeliveryRuntime(
        rs.getLong("company_id"),
        rs.getLong("run_id"),
        rs.getLong("sender_id"),
        rs.getLong("connection_id"),
        rs.getLong("tenant_id"),
        instant(rs, "tenant_created_at"),
        rs.getString("tenant_status"),
        rs.getBoolean("tenant_sending_enabled"),
        instant(rs, "tenant_last_provider_sync_at"),
        rs.getLong("dlt_profile_id"),
        rs.getString("pe_id_snapshot"),
        rs.getString("profile_status"),
        rs.getString("tm_chain_status"),
        instant(rs, "profile_checked_at"),
        instant(rs, "profile_stale_after"),
        rs.getLong("header_id"),
        rs.getString("header_snapshot"),
        rs.getString("message_category"),
        rs.getString("header_status"),
        instant(rs, "header_checked_at"),
        instant(rs, "header_stale_after"),
        rs.getLong("template_id"),
        rs.getLong("template_version_id"),
        rs.getString("dlt_template_id_snapshot"),
        rs.getString("approved_body_snapshot"),
        rs.getString("body_fingerprint_snapshot"),
        rs.getString("variables_schema_snapshot"),
        rs.getString("cta_policy_snapshot"),
        rs.getString("template_version_status"),
        instant(rs, "template_checked_at"),
        instant(rs, "template_stale_after"),
        rs.getLong("provider_binding_id"),
        rs.getString("provider_mode"),
        rs.getString("provider_account_key"),
        rs.getString("provider_template_ref_snapshot"),
        rs.getString("provider_flow_ref_snapshot"),
        rs.getString("ruleset_version_snapshot"),
        rs.getString("provider_binding_status"),
        instant(rs, "provider_binding_last_sync_at"),
        rs.getString("connection_status"),
        rs.getBoolean("connection_sending_enabled"),
        rs.getString("management_mode"),
        rs.getString("sender_status"));
  }

  private Instant instant(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }
}

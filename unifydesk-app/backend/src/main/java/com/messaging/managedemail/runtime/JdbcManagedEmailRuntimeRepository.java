package com.messaging.managedemail.runtime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcManagedEmailRuntimeRepository implements ManagedEmailRuntimeRepository {

  private static final String SENDER_SQL =
      """
      select s.company_id,
             s.id as sender_id,
             c.id as connection_id,
             b.tenant_id,
             b.domain_id,
             s.status as sender_status,
             c.status as connection_status,
             c.management_mode,
             c.sending_enabled as connection_sending_enabled,
             t.status as tenant_status,
             t.sending_status as tenant_sending_status,
             t.reputation_status as tenant_reputation_status,
             t.ramp_stage,
             d.status as domain_status,
             d.normalized_domain,
             t.region,
             t.provider_tenant_name,
             t.provider_configuration_set_name,
             c.max_send_rate,
             c.max_24h_send
        from broadcast_senders s
        join broadcast_sender_connections c
          on c.id = s.connection_id
        join managed_email_sender_bindings b
          on b.sender_id = s.id
         and b.company_id = s.company_id
        join managed_email_domains d
          on d.id = b.domain_id
         and d.company_id = b.company_id
        join managed_email_tenants t
          on t.id = b.tenant_id
         and t.company_id = b.company_id
       where s.id = ?
         and s.company_id = ?
         and c.company_id = ?
      """;

  private static final String RECIPIENT_SQL =
      """
      select r.id as recipient_id,
             r.company_id,
             r.run_id,
             r.sender_id,
             c.id as connection_id,
             b.tenant_id,
             b.domain_id,
             r.status as recipient_status,
             r.normalized_destination,
             r.provider_message_id,
             s.status as sender_status,
             c.status as connection_status,
             c.management_mode,
             c.sending_enabled as connection_sending_enabled,
             t.status as tenant_status,
             t.sending_status as tenant_sending_status,
             t.reputation_status as tenant_reputation_status,
             t.ramp_stage,
             d.status as domain_status,
             d.normalized_domain,
             t.region,
             t.provider_tenant_name,
             t.provider_configuration_set_name,
             c.max_send_rate,
             c.max_24h_send
        from broadcast_recipients r
        join broadcast_runs run
          on run.id = r.run_id
         and run.company_id = r.company_id
         and run.sender_id = r.sender_id
        join broadcast_senders s
          on s.id = r.sender_id
         and s.company_id = r.company_id
        join broadcast_sender_connections c
          on c.id = s.connection_id
         and c.company_id = r.company_id
        join managed_email_sender_bindings b
          on b.sender_id = s.id
         and b.company_id = r.company_id
        join managed_email_domains d
          on d.id = b.domain_id
         and d.company_id = r.company_id
        join managed_email_tenants t
          on t.id = b.tenant_id
         and t.company_id = r.company_id
       where r.id = ?
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  @Transactional(readOnly = true)
  public Optional<ManagedSenderRuntime> findSenderRuntime(Long companyId, Long senderId) {
    try {
      return Optional.of(
          jdbcTemplate.queryForObject(
              SENDER_SQL,
              (resultSet, rowNum) -> senderRuntime(resultSet),
              senderId,
              companyId,
              companyId));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ManagedRecipientRuntime> findRecipientRuntime(Long recipientId) {
    try {
      return Optional.of(
          jdbcTemplate.queryForObject(
              RECIPIENT_SQL, (resultSet, rowNum) -> recipientRuntime(resultSet), recipientId));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ManagedTenantRuntime> findTenantRuntime(Long companyId) {
    try {
      return Optional.of(
          jdbcTemplate.queryForObject(
              """
              select company_id, id as tenant_id, status, sending_status, reputation_status,
                     ramp_stage, region, provider_tenant_name
                from managed_email_tenants
               where company_id = ?
              """,
              (resultSet, rowNum) ->
                  new ManagedTenantRuntime(
                      resultSet.getLong("company_id"),
                      resultSet.getLong("tenant_id"),
                      resultSet.getString("status"),
                      resultSet.getString("sending_status"),
                      resultSet.getString("reputation_status"),
                      resultSet.getString("ramp_stage"),
                      resultSet.getString("region"),
                      resultSet.getString("provider_tenant_name")),
              companyId));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  @Override
  @Transactional
  public void suspendTenant(Long companyId, Long tenantId, String reason) {
    jdbcTemplate.update(
        """
        update managed_email_tenants
           set status = 'SUSPENDED',
               sending_status = 'DISABLED',
               suspension_reason = ?,
               suspended_at = coalesce(suspended_at, now()),
               updated_at = now(),
               version = version + 1
         where id = ?
           and company_id = ?
           and status <> 'DELETED'
        """,
        reason,
        tenantId,
        companyId);
  }

  private ManagedSenderRuntime senderRuntime(ResultSet resultSet) throws SQLException {
    return new ManagedSenderRuntime(
        resultSet.getLong("company_id"),
        resultSet.getLong("sender_id"),
        resultSet.getLong("connection_id"),
        resultSet.getLong("tenant_id"),
        resultSet.getLong("domain_id"),
        resultSet.getString("sender_status"),
        resultSet.getString("connection_status"),
        resultSet.getString("management_mode"),
        resultSet.getBoolean("connection_sending_enabled"),
        resultSet.getString("tenant_status"),
        resultSet.getString("tenant_sending_status"),
        resultSet.getString("tenant_reputation_status"),
        resultSet.getString("ramp_stage"),
        resultSet.getString("domain_status"),
        resultSet.getString("normalized_domain"),
        resultSet.getString("region"),
        resultSet.getString("provider_tenant_name"),
        resultSet.getString("provider_configuration_set_name"),
        nullableDouble(resultSet, "max_send_rate"),
        nullableDouble(resultSet, "max_24h_send"));
  }

  private ManagedRecipientRuntime recipientRuntime(ResultSet resultSet) throws SQLException {
    return new ManagedRecipientRuntime(
        resultSet.getLong("recipient_id"),
        resultSet.getLong("company_id"),
        resultSet.getLong("run_id"),
        resultSet.getLong("sender_id"),
        resultSet.getLong("connection_id"),
        resultSet.getLong("tenant_id"),
        resultSet.getLong("domain_id"),
        resultSet.getString("recipient_status"),
        resultSet.getString("normalized_destination"),
        resultSet.getString("provider_message_id"),
        resultSet.getString("sender_status"),
        resultSet.getString("connection_status"),
        resultSet.getString("management_mode"),
        resultSet.getBoolean("connection_sending_enabled"),
        resultSet.getString("tenant_status"),
        resultSet.getString("tenant_sending_status"),
        resultSet.getString("tenant_reputation_status"),
        resultSet.getString("ramp_stage"),
        resultSet.getString("domain_status"),
        resultSet.getString("normalized_domain"),
        resultSet.getString("region"),
        resultSet.getString("provider_tenant_name"),
        resultSet.getString("provider_configuration_set_name"),
        nullableDouble(resultSet, "max_send_rate"),
        nullableDouble(resultSet, "max_24h_send"));
  }

  private Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
    double value = resultSet.getDouble(column);
    return resultSet.wasNull() ? null : value;
  }
}

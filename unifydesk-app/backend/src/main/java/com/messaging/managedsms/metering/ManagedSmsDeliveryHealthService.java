package com.messaging.managedsms.metering;

import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsDeliveryHealthService {
  private final JdbcTemplate jdbcTemplate;
  private final ManagedSmsRuntimeRepository runtimeRepository;
  private final ManagedSmsRuntimeProperties properties;

  @Transactional(readOnly = true)
  public DeliveryHealth get(Long companyId) {
    Aggregate aggregate =
        jdbcTemplate.queryForObject(
            """
            select count(*) filter (where d.last_provider_status = 'DELIVERED') as delivered,
                   count(*) filter (where d.last_provider_status = 'FAILED') as failed,
                   count(*) filter (where d.last_provider_status = 'REJECTED') as rejected,
                   (
                     select count(*)
                       from broadcast_recipients r
                       join managed_sms_run_bindings rb
                         on rb.run_id = r.run_id and rb.company_id = r.company_id
                      where r.company_id = ?
                        and r.status = 'UNKNOWN'
                   ) as unknown_count
              from managed_sms_recipient_delivery d
             where d.company_id = ?
            """,
            (rs, rowNum) ->
                new Aggregate(
                    rs.getLong("delivered"),
                    rs.getLong("failed"),
                    rs.getLong("rejected"),
                    rs.getLong("unknown_count")),
            companyId,
            companyId);
    if (aggregate == null) aggregate = new Aggregate(0, 0, 0, 0);

    boolean enabled =
        properties.providerConfigured() && runtimeRepository.tenantSendingEnabled(companyId);
    String status;
    String action;
    if (!enabled) {
      status = "SUSPENDED";
      action = "Managed SMS sending is currently unavailable.";
    } else if (aggregate.failed() > 0 || aggregate.rejected() > 0 || aggregate.unknown() > 0) {
      status = "DEGRADED";
      action = null;
    } else {
      status = "HEALTHY";
      action = null;
    }
    DeliveryHealth body =
        new DeliveryHealth(
            status,
            aggregate.delivered(),
            aggregate.failed(),
            aggregate.rejected(),
            aggregate.unknown(),
            action);
    return body;
  }

  private record Aggregate(long delivered, long failed, long rejected, long unknown) {}

  public record DeliveryHealth(
      String status,
      long delivered,
      long failed,
      long rejected,
      long unknown,
      String actionRequired) {}
}

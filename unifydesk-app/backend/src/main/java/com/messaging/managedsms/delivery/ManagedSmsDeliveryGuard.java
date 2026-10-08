package com.messaging.managedsms.delivery;

import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import com.messaging.managedsms.safety.ManagedSmsProviderSafetyService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedSmsDeliveryGuard {

  private final ManagedSmsRuntimeRepository runtimeRepository;
  private final ManagedSmsRuntimeProperties properties;
  private final ManagedSmsProviderSafetyService safetyService;
  private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  public Optional<ManagedSmsRuntimeRepository.DeliveryRuntime> eligible(
      BroadcastRun run,
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastRecipient recipient) {
    if (!properties.providerConfigured()
        || connection.getManagementMode() != BroadcastSenderManagementMode.MESSAGING_MANAGED
        || run.getProviderType() != BroadcastSenderProvider.MSG91_SMS
        || sender.getProvider() != BroadcastSenderProvider.MSG91_SMS
        || connection.getProvider() != BroadcastSenderProvider.MSG91_SMS) {
      return Optional.empty();
    }
    // Defend historical run routing against accidental provider changes and unsupported adapters.
    java.util.List<String> route = jdbcTemplate.queryForList(
        "select delivery_provider from managed_sms_run_bindings where run_id=? and company_id=?",
        String.class, run.getId(), run.getCompanyId());
    if (route.size() != 1 || !"MSG91_SMS".equals(route.getFirst())) return Optional.empty();
    Instant now = Instant.now();
    return runtimeRepository
        .findDeliveryRuntime(run.getCompanyId(), run.getId(), sender.getId())
        .filter(ManagedSmsRuntimeRepository.DeliveryRuntime::currentlySendable)
        .filter(runtime -> runtime.companyId().equals(recipient.getCompanyId()))
        .filter(runtime -> runtime.runId().equals(recipient.getRunId()))
        .filter(runtime -> runtime.senderId().equals(recipient.getSenderId()))
        .filter(runtime -> runtime.connectionId().equals(connection.getId()))
        .filter(runtime -> fresh(runtime, now))
        .filter(runtime -> safetyService.isAvailable(runtime.providerAccountKey()));
  }

  boolean fresh(ManagedSmsRuntimeRepository.DeliveryRuntime runtime, Instant now) {
    return freshFact(runtime.profileCheckedAt(), runtime.profileStaleAfter(), now)
        && freshFact(runtime.headerCheckedAt(), runtime.headerStaleAfter(), now)
        && freshFact(runtime.templateCheckedAt(), runtime.templateStaleAfter(), now)
        && freshByAge(runtime.providerBindingLastSyncAt(), now)
        && freshByAge(runtime.tenantLastProviderSyncAt(), now);
  }

  private boolean freshFact(Instant checkedAt, Instant staleAfter, Instant now) {
    return checkedAt != null && staleAfter != null && staleAfter.isAfter(now);
  }

  private boolean freshByAge(Instant checkedAt, Instant now) {
    Duration maxAge = properties.getComplianceStaleAfter();
    return checkedAt != null
        && maxAge != null
        && !maxAge.isZero()
        && !maxAge.isNegative()
        && checkedAt.plus(maxAge).isAfter(now);
  }
}

package com.messaging.managedsms.safety;

import com.messaging.common.id.IdGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsProviderObservationService {

  private final JdbcTemplate jdbcTemplate;
  private final ManagedSmsProviderObservationAdapter adapter;
  private final com.messaging.managedsms.metareach.MetaReachProperties metaReach;

  @Scheduled(fixedDelayString = "${app.managed-sms.provider-observation-poll-ms:60000}")
  public void refreshKnownAccounts() {
    List<AccountRef> accounts =
        jdbcTemplate.query(
            """
            select distinct
                   case
                     when provider_mode = 'SHARED_MASTER' then 'master'
                     else coalesce(provider_client_ref, provider_account_ref, '')
                   end as provider_account_key,
                   provider_client_ref
              from managed_sms_tenants
             where status in ('READY', 'PROVIDER_SYNCING')
               and sending_enabled = true
            """,
            (rs, rowNum) ->
                new AccountRef(
                    rs.getString("provider_account_key"),
                    rs.getString("provider_client_ref")));
    for (AccountRef account : accounts) {
      if (account.providerAccountKey() == null || account.providerAccountKey().isBlank()) continue;
      try {
        refresh(account.providerAccountKey(), account.providerClientRef());
      } catch (RuntimeException ignored) {
        // Observation failure is represented by absence of a fresh healthy snapshot.
      }
    }
    if (metaReach.ready()) {
      try { refresh(metaReach.accountKey(),null); }
      catch (RuntimeException ignored) { /* No fabricated healthy snapshot. */ }
    }
  }

  @Transactional
  public boolean refresh(String providerAccountKey, String providerClientRef) {
    Optional<ManagedSmsProviderObservationAdapter.ProviderObservation> observationOpt =
        adapter.observe(providerAccountKey, providerClientRef);
    if (observationOpt.isEmpty()) return false;

    ManagedSmsProviderObservationAdapter.ProviderObservation observation = observationOpt.get();
    if (!providerAccountKey.equals(observation.providerAccountKey())
        || observation.observedAt() == null
        || observation.validUntil() == null
        || !observation.validUntil().isAfter(observation.observedAt())
        || observation.validUntil().isBefore(Instant.now())
        || observation.source() == null
        || observation.source().isBlank()) {
      return false;
    }

    String persistedSource = "VERIFIED_ADAPTER:" + observation.source().trim();
    if (persistedSource.length() > 80) return false;

    jdbcTemplate.update(
        """
        insert into managed_sms_provider_snapshots (
          id, provider_account_key, provider_client_ref, balance, credit_limit,
          max_send_rate, sending_enabled, observed_at, valid_until, observation_source
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        on conflict (provider_account_key, observed_at) do nothing
        """,
        IdGenerator.nextId(),
        observation.providerAccountKey(),
        observation.providerClientRef(),
        observation.balance(),
        observation.creditLimit(),
        observation.maxSendRate(),
        observation.sendingEnabled(),
        Timestamp.from(observation.observedAt()),
        Timestamp.from(observation.validUntil()),
        persistedSource);
    return true;
  }

  private record AccountRef(String providerAccountKey, String providerClientRef) {}
}

package com.messaging.managedemail.control.service;

import com.messaging.common.id.IdGenerator;
import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.entity.ManagedEmailTenant;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.*;
import com.messaging.managedemail.control.provisioning.ManagedEmailProvisioningService;
import com.messaging.managedemail.control.repository.ManagedEmailTenantRepository;
import java.security.SecureRandom;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedEmailTenantService {

  private static final long COMPANY_LOCK_NAMESPACE = 0x4d454d41494c0000L;

  private final ManagedEmailTenantRepository tenantRepository;
  private final ManagedEmailProvisioningService provisioningService;
  private final ManagedEmailProperties properties;
  private final JdbcTemplate jdbcTemplate;
  private final SecureRandom secureRandom = new SecureRandom();

  @Transactional
  public ManagedEmailTenant ensureDesired(Long companyId) {
    advisoryLock(COMPANY_LOCK_NAMESPACE ^ companyId);
    return tenantRepository
        .findByCompanyId(companyId)
        .orElseGet(
            () -> {
              String token = opaqueToken();
              ManagedEmailTenant tenant = new ManagedEmailTenant();
              tenant.setId(IdGenerator.nextId());
              tenant.setCompanyId(companyId);
              tenant.setRegion(properties.getAwsRegion());
              tenant.setProviderTenantName("ud-c-" + token);
              tenant.setProviderConfigurationSetName("ud-c-" + token);
              tenant.setStatus(TenantStatus.PROVISIONING);
              tenant.setSendingStatus(SendingStatus.DISABLED);
              tenant.setRampStage(RampStage.NEW);
              ManagedEmailTenant saved = tenantRepository.save(tenant);
              provisioningService.enqueue(
                  companyId,
                  ProvisioningResourceType.TENANT,
                  saved.getId(),
                  ProvisioningOperationType.ENSURE_TENANT,
                  "tenant:ensure:" + companyId);
              return saved;
            });
  }

  private void advisoryLock(long key) {
    jdbcTemplate.queryForObject(
        "select pg_advisory_xact_lock(?)::text", String.class, key);
  }

  private String opaqueToken() {
    byte[] bytes = new byte[12];
    secureRandom.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}

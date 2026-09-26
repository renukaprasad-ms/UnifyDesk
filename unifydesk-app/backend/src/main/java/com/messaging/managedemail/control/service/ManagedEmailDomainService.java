package com.messaging.managedemail.control.service;

import com.messaging.common.exception.*;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.dto.ManagedEmailDtos.*;
import com.messaging.managedemail.control.entity.*;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.*;
import com.messaging.managedemail.control.provisioning.ManagedEmailProvisioningService;
import com.messaging.managedemail.control.repository.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedEmailDomainService {

  private static final long DOMAIN_LOCK_NAMESPACE = 0x444f4d41494e0000L;
  private static final Set<DomainStatus> TERMINAL =
      Set.of(DomainStatus.DELETED, DomainStatus.RETIRED);

  private final ManagedEmailDomainRepository domainRepository;
  private final ManagedEmailTenantService tenantService;
  private final ManagedEmailEntitlementService entitlementService;
  private final ManagedEmailProvisioningService provisioningService;
  private final ManagedEmailResponseMapper mapper;
  private final ManagedEmailDomainNameService domainNameService;
  private final ManagedEmailSenderLifecycleService senderLifecycleService;
  private final CompanyRepository companyRepository;
  private final ManagedEmailProperties properties;
  private final JdbcTemplate jdbcTemplate;

  @Transactional(readOnly = true)
  public List<DomainResponse> list(Long companyId) {
    return domainRepository.findByCompanyIdOrderByUpdatedAtDesc(companyId).stream()
        .map(mapper::domain)
        .toList();
  }

  @Transactional(readOnly = true)
  public DomainResponse get(Long companyId, Long domainId) {
    return mapper.domain(requireDomain(companyId, domainId));
  }

  @Transactional
  public CreateResult<DomainResponse> createCustomer(
      Long companyId, Long userId, CustomerDomainRequest request) {
    entitlementService.requireEnabled(companyId);
    String operationKey =
        provisioningService.requestKey("customer-domain", companyId, request.idempotencyKey());
    var replay = provisioningService.find(operationKey);
    if (replay.isPresent()) {
      ManagedEmailDomain existing =
          domainRepository
              .findByIdAndCompanyId(replay.get().getResourceId(), companyId)
              .orElseThrow(() -> new NotFoundException("Managed Email domain not found"));
      return new CreateResult<>(mapper.domain(existing), true);
    }

    String normalized = domainNameService.normalizeCustomerDomain(request.domain());
    domainLock(normalized);
    var claimed =
        domainRepository.findLiveClaim(DomainType.CUSTOMER_DOMAIN, normalized, TERMINAL);
    if (claimed.isPresent()) {
      if (claimed.get().getCompanyId().equals(companyId)) {
        return new CreateResult<>(mapper.domain(claimed.get()), true);
      }
      throw new ConflictException("This domain is unavailable for Managed Email");
    }

    entitlementService.requireDomainCapacity(companyId);
    ManagedEmailTenant tenant = tenantService.ensureDesired(companyId);

    ManagedEmailDomain domain = new ManagedEmailDomain();
    domain.setCompanyId(companyId);
    domain.setTenantId(tenant.getId());
    domain.setDomainType(DomainType.CUSTOMER_DOMAIN);
    domain.setNormalizedDomain(normalized);
    domain.setDisplayDomain(normalized);
    domain.setStatus(DomainStatus.PROVISIONING);
    domain.setVerificationStatus(VerificationStatus.PENDING);
    domain.setDkimStatus(DkimStatus.PENDING);
    domain.setDmarcStatus(DmarcStatus.UNKNOWN);
    domain.setCreatedByUserId(userId);
    ManagedEmailDomain saved = domainRepository.save(domain);

    provisioningService.enqueue(
        companyId,
        ProvisioningResourceType.DOMAIN,
        saved.getId(),
        ProvisioningOperationType.ENSURE_DOMAIN_IDENTITY,
        operationKey);
    return new CreateResult<>(mapper.domain(saved), false);
  }

  @Transactional
  public CreateResult<DomainResponse> createHosted(
      Long companyId, Long userId, HostedDomainRequest request) {
    entitlementService.requireEnabled(companyId);
    String operationKey =
        provisioningService.requestKey("hosted-domain", companyId, request.idempotencyKey());
    var replay = provisioningService.find(operationKey);
    if (replay.isPresent()) {
      ManagedEmailDomain existing =
          domainRepository
              .findByIdAndCompanyId(replay.get().getResourceId(), companyId)
              .orElseThrow(() -> new NotFoundException("Managed Email domain not found"));
      return new CreateResult<>(mapper.domain(existing), true);
    }

    entitlementService.requireDomainCapacity(companyId);
    Company company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new NotFoundException("Company not found"));
    ManagedEmailTenant tenant = tenantService.ensureDesired(companyId);

    ManagedEmailDomainNameService.HostedName hosted = null;
    for (int attempt = 0; attempt < 8; attempt++) {
      ManagedEmailDomainNameService.HostedName candidate =
          domainNameService.generateHostedName(company.getName(), properties.getHostedBaseDomain());
      if (!domainRepository.existsByNormalizedDomainIgnoreCase(candidate.domain())) {
        hosted = candidate;
        break;
      }
    }
    if (hosted == null) {
      throw new ServiceUnavailableException("Unable to reserve a Managed Email domain");
    }

    ManagedEmailDomain domain = new ManagedEmailDomain();
    domain.setCompanyId(companyId);
    domain.setTenantId(tenant.getId());
    domain.setDomainType(DomainType.UNIFYDESK_HOSTED);
    domain.setNormalizedDomain(hosted.domain());
    domain.setDisplayDomain(hosted.domain());
    domain.setHostedSlug(hosted.slug());
    domain.setHostedReservationToken(hosted.reservationToken());
    domain.setStatus(DomainStatus.PROVISIONING);
    domain.setVerificationStatus(VerificationStatus.PENDING);
    domain.setDkimStatus(DkimStatus.PENDING);
    domain.setDmarcStatus(DmarcStatus.UNKNOWN);
    domain.setCreatedByUserId(userId);
    ManagedEmailDomain saved = domainRepository.save(domain);

    provisioningService.enqueue(
        companyId,
        ProvisioningResourceType.DOMAIN,
        saved.getId(),
        ProvisioningOperationType.ENSURE_DOMAIN_IDENTITY,
        operationKey);
    return new CreateResult<>(mapper.domain(saved), false);
  }

  @Transactional
  public DomainResponse refresh(Long companyId, Long domainId) {
    entitlementService.requireEnabled(companyId);
    ManagedEmailDomain domain = requireDomain(companyId, domainId);
    if (domain.isTerminal() || domain.getStatus() == DomainStatus.DELETING) {
      return mapper.domain(domain);
    }
    Instant recently = Instant.now().minus(Duration.ofSeconds(5));
    if (domain.getLastProviderSyncAt() == null
        || domain.getLastProviderSyncAt().isBefore(recently)) {
      provisioningService.enqueueOrReset(
          companyId,
          ProvisioningResourceType.DOMAIN,
          domainId,
          ProvisioningOperationType.REFRESH_DOMAIN_VERIFICATION,
          "domain:refresh:" + domainId);
    }
    return mapper.domain(domain);
  }

  @Transactional
  public DomainResponse delete(Long companyId, Long domainId) {
    ManagedEmailDomain domain = requireDomain(companyId, domainId);
    if (domain.isTerminal()) return mapper.domain(domain);

    domain.setStatus(DomainStatus.DELETING);
    domain.setSuspendedAt(Instant.now());
    domainRepository.save(domain);
    senderLifecycleService.setDomainSendersEnabled(companyId, domainId, false);
    provisioningService.enqueueOrReset(
        companyId,
        ProvisioningResourceType.DOMAIN,
        domainId,
        ProvisioningOperationType.DELETE_DOMAIN_IDENTITY,
        "domain:delete:" + domainId);
    return mapper.domain(domain);
  }

  @Transactional
  public int queueVerificationRefreshes() {
    Instant before = Instant.now().minus(properties.getVerificationRefreshInterval());
    List<ManagedEmailDomain> candidates =
        domainRepository.findVerificationCandidates(
            Set.of(
                DomainStatus.PENDING_DNS,
                DomainStatus.VERIFYING,
                DomainStatus.ACTIVE,
                DomainStatus.SUSPENDED_VERIFICATION),
            before,
            PageRequest.of(0, 100));
    for (ManagedEmailDomain domain : candidates) {
      provisioningService.enqueueOrReset(
          domain.getCompanyId(),
          ProvisioningResourceType.DOMAIN,
          domain.getId(),
          ProvisioningOperationType.REFRESH_DOMAIN_VERIFICATION,
          "domain:verify:" + domain.getId());
    }
    return candidates.size();
  }

  @Transactional(readOnly = true)
  public ManagedEmailDomain requireDomain(Long companyId, Long domainId) {
    return domainRepository
        .findByIdAndCompanyId(domainId, companyId)
        .orElseThrow(() -> new NotFoundException("Managed Email domain not found"));
  }

  private void domainLock(String normalizedDomain) {
    long key = DOMAIN_LOCK_NAMESPACE ^ normalizedDomain.hashCode();
    jdbcTemplate.queryForObject("select pg_advisory_xact_lock(?)::text", String.class, key);
  }
}

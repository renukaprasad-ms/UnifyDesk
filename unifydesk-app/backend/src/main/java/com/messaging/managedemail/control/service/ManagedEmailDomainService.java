package com.messaging.managedemail.control.service;

import com.messaging.common.exception.*;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.managedemail.control.config.ManagedEmailProperties;
import com.messaging.managedemail.control.dto.ManagedEmailAdminDtos.*;
import com.messaging.managedemail.control.dto.ManagedEmailDtos.*;
import com.messaging.managedemail.control.entity.*;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.*;
import com.messaging.managedemail.control.provisioning.ManagedEmailProvisioningService;
import com.messaging.managedemail.control.repository.*;
import com.messaging.user.entity.User;
import com.messaging.user.repository.UserRepository;
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
      Set.of(DomainStatus.DELETED, DomainStatus.RETIRED, DomainStatus.REJECTED);

  private final ManagedEmailDomainRepository domainRepository;
  private final ManagedEmailTenantService tenantService;
  private final ManagedEmailEntitlementService entitlementService;
  private final ManagedEmailProvisioningService provisioningService;
  private final ManagedEmailResponseMapper mapper;
  private final ManagedEmailDomainNameService domainNameService;
  private final ManagedEmailSenderLifecycleService senderLifecycleService;
  private final CompanyRepository companyRepository;
  private final UserRepository userRepository;
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

  @Transactional(readOnly = true)
  public SubdomainAvailabilityResponse checkSubdomainAvailability(String rawSubdomain) {
    if (rawSubdomain == null || rawSubdomain.isBlank()) {
      return new SubdomainAvailabilityResponse(false, "", null, "Subdomain cannot be blank");
    }
    final String cleaned;
    try {
      cleaned = domainNameService.validateHostedSubdomain(rawSubdomain);
    } catch (BadRequestException e) {
      return new SubdomainAvailabilityResponse(false, rawSubdomain.trim(), null, e.getMessage());
    }
    String fullDomain =
        domainNameService.buildHostedDomain(cleaned, properties.getHostedBaseDomain());
    if (domainRepository.existsByNormalizedDomainIgnoreCase(fullDomain)) {
      return new SubdomainAvailabilityResponse(
          false,
          cleaned,
          fullDomain,
          "The subdomain '" + cleaned + "' is already in use. Please choose another one.");
    }
    return new SubdomainAvailabilityResponse(
        true, cleaned, fullDomain, "The subdomain '" + cleaned + "' is available!");
  }

  @Transactional
  public CreateResult<DomainResponse> createHosted(
      Long companyId, Long userId, HostedDomainRequest request) {
    entitlementService.requireEnabled(companyId);

    // 1. Validate requested subdomain (syntax, reserved words, prohibited terms)
    String cleanedSubdomain = domainNameService.validateHostedSubdomain(request.desiredSubdomain());
    String fullDomain =
        domainNameService.buildHostedDomain(cleanedSubdomain, properties.getHostedBaseDomain());

    // 2. Critical pre-check: check if domain already exists in DB -> throw ConflictException
    if (domainRepository.existsByNormalizedDomainIgnoreCase(fullDomain)) {
      throw new ConflictException(
          "The subdomain '" + cleanedSubdomain + "' is already in use. Please choose another one.");
    }

    // 3. Ensure this company does not already have an active request pending approval
    boolean hasPending =
        domainRepository.findByCompanyIdOrderByUpdatedAtDesc(companyId).stream()
            .anyMatch(d -> d.getStatus() == DomainStatus.PENDING_APPROVAL);
    if (hasPending) {
      throw new ConflictException(
          "A hosted domain request is already pending administrator approval for this workspace.");
    }

    entitlementService.requireDomainCapacity(companyId);
    companyRepository
        .findById(companyId)
        .orElseThrow(() -> new NotFoundException("Company not found"));
    ManagedEmailTenant tenant = tenantService.ensureDesired(companyId);

    // 4. Save the hosted domain request in PENDING_APPROVAL status (provisioning enqueued upon approval)
    ManagedEmailDomain domain = new ManagedEmailDomain();
    domain.setCompanyId(companyId);
    domain.setTenantId(tenant.getId());
    domain.setDomainType(DomainType.UNIFYDESK_HOSTED);
    domain.setNormalizedDomain(fullDomain);
    domain.setDisplayDomain(fullDomain);
    domain.setHostedSlug(cleanedSubdomain);
    domain.setHostedReservationToken(domainNameService.randomReservationToken());
    domain.setRequestedSubdomain(cleanedSubdomain);
    domain.setStatus(DomainStatus.PENDING_APPROVAL);
    domain.setVerificationStatus(VerificationStatus.PENDING);
    domain.setDkimStatus(DkimStatus.PENDING);
    domain.setDmarcStatus(DmarcStatus.UNKNOWN);
    domain.setCreatedByUserId(userId);
    domain.setAdminAssignedAlternative(false);
    ManagedEmailDomain saved = domainRepository.save(domain);

    return new CreateResult<>(mapper.domain(saved), false);
  }

  @Transactional(readOnly = true)
  public List<HostedDomainReviewItem> listPendingHostedRequests() {
    List<ManagedEmailDomain> pendings =
        domainRepository.findByStatusOrderByCreatedAtDesc(DomainStatus.PENDING_APPROVAL);
    return pendings.stream()
        .map(
            domain -> {
              String companyName =
                  companyRepository
                      .findById(domain.getCompanyId())
                      .map(Company::getName)
                      .orElse("Unknown Company");
              String userEmail =
                  userRepository
                      .findById(domain.getCreatedByUserId())
                      .map(User::getEmail)
                      .orElse(null);
              return new HostedDomainReviewItem(
                  domain.getId().toString(),
                  domain.getCompanyId(),
                  companyName,
                  domain.getRequestedSubdomain(),
                  domain.getDisplayDomain(),
                  domain.getStatus(),
                  domain.getCreatedAt(),
                  domain.getCreatedByUserId(),
                  userEmail,
                  domain.getRejectionReason(),
                  domain.isAdminAssignedAlternative());
            })
        .toList();
  }

  @Transactional
  public DomainResponse decideHostedRequest(
      Long domainId, Long adminUserId, DomainDecisionRequest decision) {
    ManagedEmailDomain domain =
        domainRepository
            .findById(domainId)
            .orElseThrow(() -> new NotFoundException("Domain request not found"));
    if (domain.getStatus() != DomainStatus.PENDING_APPROVAL) {
      throw new BadRequestException(
          "Domain request is not pending approval (current status: " + domain.getStatus() + ")");
    }

    domain.setReviewedByUserId(adminUserId);
    domain.setReviewedAt(Instant.now());

    switch (decision.action()) {
      case APPROVE -> {
        domain.setStatus(DomainStatus.PROVISIONING);
        ManagedEmailDomain saved = domainRepository.save(domain);
        String operationKey =
            provisioningService.requestKey(
                "hosted-domain", domain.getCompanyId(), "approved-" + domain.getId());
        provisioningService.enqueue(
            domain.getCompanyId(),
            ProvisioningResourceType.DOMAIN,
            saved.getId(),
            ProvisioningOperationType.ENSURE_DOMAIN_IDENTITY,
            operationKey);
        return mapper.domain(saved);
      }
      case ASSIGN_ALTERNATIVE -> {
        if (decision.alternativeSubdomain() == null || decision.alternativeSubdomain().isBlank()) {
          throw new BadRequestException(
              "An alternative subdomain is required when action is ASSIGN_ALTERNATIVE");
        }
        String cleanAlternative =
            domainNameService.validateHostedSubdomain(decision.alternativeSubdomain());
        String alternativeDomain =
            domainNameService.buildHostedDomain(
                cleanAlternative, properties.getHostedBaseDomain());
        if (domainRepository.existsByNormalizedDomainIgnoreCase(alternativeDomain)) {
          throw new ConflictException(
              "The alternative subdomain '" + cleanAlternative + "' is already in use.");
        }
        domain.setHostedSlug(cleanAlternative);
        domain.setNormalizedDomain(alternativeDomain);
        domain.setDisplayDomain(alternativeDomain);
        domain.setAdminAssignedAlternative(true);
        domain.setStatus(DomainStatus.PROVISIONING);
        ManagedEmailDomain saved = domainRepository.save(domain);
        String operationKey =
            provisioningService.requestKey(
                "hosted-domain", domain.getCompanyId(), "approved-alt-" + domain.getId());
        provisioningService.enqueue(
            domain.getCompanyId(),
            ProvisioningResourceType.DOMAIN,
            saved.getId(),
            ProvisioningOperationType.ENSURE_DOMAIN_IDENTITY,
            operationKey);
        return mapper.domain(saved);
      }
      case REJECT -> {
        if (decision.reason() == null || decision.reason().isBlank()) {
          throw new BadRequestException(
              "A rejection reason is required when rejecting a domain request");
        }
        domain.setStatus(DomainStatus.REJECTED);
        domain.setRejectionReason(decision.reason().trim());
        ManagedEmailDomain saved = domainRepository.save(domain);
        return mapper.domain(saved);
      }
      default -> throw new BadRequestException("Unsupported decision action: " + decision.action());
    }
  }

  @Transactional
  public DomainResponse refresh(Long companyId, Long domainId) {
    entitlementService.requireEnabled(companyId);
    ManagedEmailDomain domain = requireDomain(companyId, domainId);
    if (domain.isTerminal()
        || domain.getStatus() == DomainStatus.DELETING
        || domain.getStatus() == DomainStatus.PENDING_APPROVAL) {
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
    if (domain.getStatus() == DomainStatus.PENDING_APPROVAL
        || domain.getStatus() == DomainStatus.REJECTED) {
      domain.setStatus(DomainStatus.DELETED);
      return mapper.domain(domainRepository.save(domain));
    }

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

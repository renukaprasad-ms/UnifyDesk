package com.messaging.emailprovider.domain.service;

import com.messaging.emailprovider.common.exception.ConflictException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.dkim.entity.DkimKey;
import com.messaging.emailprovider.dkim.service.DkimKeyService;
import com.messaging.emailprovider.dns.DnsVerificationResult;
import com.messaging.emailprovider.dns.DnsVerificationService;
import com.messaging.emailprovider.dns.DomainDnsRecordService;
import com.messaging.emailprovider.domain.dto.CreateSendingDomainRequest;
import com.messaging.emailprovider.domain.dto.SendingDomainResponse;
import com.messaging.emailprovider.domain.entity.SendingDomain;
import com.messaging.emailprovider.domain.enums.SendingDomainStatus;
import com.messaging.emailprovider.domain.repository.SendingDomainRepository;
import com.messaging.emailprovider.mail.repository.EmailMessageRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SendingDomainService {

  private final SendingDomainRepository domainRepository;
  private final EmailMessageRepository messageRepository;
  private final DomainNameNormalizer domainNameNormalizer;
  private final DomainVerificationTokenGenerator tokenGenerator;
  private final DkimKeyService dkimKeyService;
  private final DomainDnsRecordService dnsRecordService;
  private final DnsVerificationService dnsVerificationService;

  public SendingDomainService(
      SendingDomainRepository domainRepository,
      EmailMessageRepository messageRepository,
      DomainNameNormalizer domainNameNormalizer,
      DomainVerificationTokenGenerator tokenGenerator,
      DkimKeyService dkimKeyService,
      DomainDnsRecordService dnsRecordService,
      DnsVerificationService dnsVerificationService) {
    this.domainRepository = domainRepository;
    this.messageRepository = messageRepository;
    this.domainNameNormalizer = domainNameNormalizer;
    this.tokenGenerator = tokenGenerator;
    this.dkimKeyService = dkimKeyService;
    this.dnsRecordService = dnsRecordService;
    this.dnsVerificationService = dnsVerificationService;
  }

  @Transactional
  public SendingDomainResponse create(Long companyId, CreateSendingDomainRequest request) {
    String normalizedDomain = domainNameNormalizer.normalize(request.domain());

    if (domainRepository.existsByDomainNameIgnoreCase(normalizedDomain)) {
      throw new ConflictException("Domain is already registered");
    }

    SendingDomain domain = new SendingDomain();
    domain.setCompanyId(companyId);
    domain.setDomainName(normalizedDomain);
    domain.setStatus(SendingDomainStatus.PENDING);
    domain.setVerificationToken(tokenGenerator.generate());
    domain = domainRepository.save(domain);

    DkimKey key = dkimKeyService.createForDomain(domain.getId());
    return SendingDomainResponse.from(domain, key, dnsRecordService);
  }

  @Transactional(readOnly = true)
  public List<SendingDomainResponse> list(Long companyId) {
    return domainRepository.findAllByCompanyIdOrderByCreatedAtDesc(companyId).stream()
        .map(
            domain ->
                SendingDomainResponse.from(
                    domain, dkimKeyService.getActiveForDomain(domain.getId()), dnsRecordService))
        .toList();
  }

  @Transactional(readOnly = true)
  public SendingDomainResponse get(Long companyId, Long domainId) {
    SendingDomain domain = requireDomain(companyId, domainId);
    return SendingDomainResponse.from(
        domain, dkimKeyService.getActiveForDomain(domain.getId()), dnsRecordService);
  }

  @Transactional
  public SendingDomainResponse verify(Long companyId, Long domainId) {
    SendingDomain domain = requireDomain(companyId, domainId);
    DkimKey key = dkimKeyService.getActiveForDomain(domain.getId());

    DnsVerificationResult result = dnsVerificationService.verify(domain, key);
    Instant now = Instant.now();
    domain.setLastCheckedAt(now);

    if (result.ownershipVerified() && domain.getOwnershipVerifiedAt() == null) {
      domain.setOwnershipVerifiedAt(now);
    }
    if (!result.ownershipVerified()) {
      domain.setOwnershipVerifiedAt(null);
    }

    if (result.dkimVerified() && domain.getDkimVerifiedAt() == null) {
      domain.setDkimVerifiedAt(now);
      key.setVerifiedAt(now);
    }
    if (!result.dkimVerified()) {
      domain.setDkimVerifiedAt(null);
      key.setVerifiedAt(null);
    }

    if (result.fullyVerified()) {
      domain.setStatus(SendingDomainStatus.VERIFIED);
      if (domain.getVerifiedAt() == null) {
        domain.setVerifiedAt(now);
      }
    } else {
      domain.setStatus(SendingDomainStatus.PENDING);
      domain.setVerifiedAt(null);
    }

    domain = domainRepository.save(domain);
    return SendingDomainResponse.from(domain, key, dnsRecordService);
  }

  @Transactional
  public void delete(Long companyId, Long domainId) {
    SendingDomain domain = requireDomain(companyId, domainId);
    if (messageRepository.existsBySendingDomainId(domainId)) {
      throw new ConflictException("Domain cannot be deleted after it has email history");
    }
    domainRepository.delete(domain);
  }

  private SendingDomain requireDomain(Long companyId, Long domainId) {
    return domainRepository
        .findByIdAndCompanyId(domainId, companyId)
        .orElseThrow(() -> new NotFoundException("Sending domain not found"));
  }
}

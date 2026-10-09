package com.messaging.emailprovider.domain.repository;

import com.messaging.emailprovider.domain.entity.SendingDomain;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SendingDomainRepository extends JpaRepository<SendingDomain, Long> {

  Optional<SendingDomain> findByDomainNameIgnoreCase(String domainName);

  Optional<SendingDomain> findByIdAndCompanyId(Long id, Long companyId);

  List<SendingDomain> findAllByCompanyIdOrderByCreatedAtDesc(Long companyId);

  boolean existsByDomainNameIgnoreCase(String domainName);
}

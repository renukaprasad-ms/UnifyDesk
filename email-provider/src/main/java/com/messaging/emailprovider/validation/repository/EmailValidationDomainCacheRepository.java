package com.messaging.emailprovider.validation.repository;

import com.messaging.emailprovider.validation.entity.EmailValidationDomainCache;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailValidationDomainCacheRepository
    extends JpaRepository<EmailValidationDomainCache, Long> {

  Optional<EmailValidationDomainCache> findByDomainAndExpiresAtAfter(String domain, Instant now);

  Optional<EmailValidationDomainCache> findByDomain(String domain);
}

package com.messaging.emailprovider.validation.repository;

import com.messaging.emailprovider.validation.entity.EmailValidationCache;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailValidationCacheRepository extends JpaRepository<EmailValidationCache, Long> {

  Optional<EmailValidationCache> findByNormalizedEmailAndExpiresAtAfter(
      String normalizedEmail, Instant now);

  Optional<EmailValidationCache> findByNormalizedEmail(String normalizedEmail);
}

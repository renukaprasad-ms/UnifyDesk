package com.messaging.emailprovider.validation.service;

import com.messaging.emailprovider.validation.core.ValidationDecision;
import com.messaging.emailprovider.validation.entity.EmailValidationCache;
import com.messaging.emailprovider.validation.repository.EmailValidationCacheRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValidationCacheService {

  private final EmailValidationCacheRepository repository;
  private final Duration ttl;

  public ValidationCacheService(
      EmailValidationCacheRepository repository,
      @Value("${app.validation.cache.email-ttl-seconds:86400}") long ttlSeconds) {
    this.repository = repository;
    this.ttl = Duration.ofSeconds(Math.max(60, ttlSeconds));
  }

  @Transactional(readOnly = true)
  public Optional<EmailValidationCache> get(String email) {
    return repository.findByNormalizedEmailAndExpiresAtAfter(email, Instant.now());
  }

  @Transactional
  public void put(String email, ValidationDecision decision) {
    Instant now = Instant.now();
    EmailValidationCache row =
        repository.findByNormalizedEmail(email).orElseGet(EmailValidationCache::new);
    row.setNormalizedEmail(email);
    row.setStatus(decision.status());
    row.setReason(decision.reason());
    row.setScore(decision.score());
    row.setDomainRoutable(decision.domainRoutable());
    row.setMxFound(decision.mxFound());
    row.setDisposable(decision.disposable());
    row.setRoleAddress(decision.roleAddress());
    row.setCatchAll(decision.catchAll());
    row.setSmtpStatus(decision.smtpStatus());
    row.setSmtpCode(decision.smtpCode());
    row.setDiagnostic(decision.diagnostic());
    row.setValidatedAt(now);
    row.setExpiresAt(now.plus(ttl));
    try {
      repository.save(row);
    } catch (DataIntegrityViolationException ignored) {
      // Another worker refreshed the same cache key concurrently.
    }
  }
}

package com.messaging.emailprovider.validation.service;

import com.messaging.emailprovider.validation.core.DomainValidationSnapshot;
import com.messaging.emailprovider.validation.core.EmailNormalizer;
import com.messaging.emailprovider.validation.core.NormalizedEmail;
import com.messaging.emailprovider.validation.core.ValidationDecision;
import com.messaging.emailprovider.validation.entity.EmailValidationCache;
import com.messaging.emailprovider.validation.entity.EmailValidationItem;
import com.messaging.emailprovider.validation.enums.SmtpProbeStatus;
import com.messaging.emailprovider.validation.enums.ValidationReason;
import com.messaging.emailprovider.validation.enums.ValidationStatus;
import com.messaging.emailprovider.validation.repository.EmailValidationItemRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ValidationBatchWorker {

  private final ValidationJobService jobs;
  private final EmailValidationItemRepository items;
  private final EmailNormalizer normalizer;
  private final ValidationDomainService domains;
  private final ValidationCacheService cache;
  private final EmailValidationEngine engine;

  public ValidationBatchWorker(
      ValidationJobService jobs,
      EmailValidationItemRepository items,
      EmailNormalizer normalizer,
      ValidationDomainService domains,
      ValidationCacheService cache,
      EmailValidationEngine engine) {
    this.jobs = jobs;
    this.items = items;
    this.normalizer = normalizer;
    this.domains = domains;
    this.cache = cache;
    this.engine = engine;
  }

  public void process(Long batchId) {
    if (!jobs.claimBatch(batchId)) {
      return;
    }

    try {
      List<EmailValidationItem> rows = items.findAllByBatchIdOrderByIdAsc(batchId);
      Map<String, DomainValidationSnapshot> domainSnapshots = new HashMap<>();
      long valid = 0;
      long invalid = 0;
      long risky = 0;
      long unknown = 0;

      for (EmailValidationItem item : rows) {
        try {
          ValidationStatus status = validateItem(item, domainSnapshots);
          switch (status) {
            case VALID -> valid++;
            case INVALID -> invalid++;
            case RISKY -> risky++;
            case UNKNOWN -> unknown++;
          }
        } catch (RuntimeException exception) {
          markInternalError(item, exception);
          unknown++;
        }
      }

      saveBatch(rows);
      jobs.completeBatch(
          batchId, new ValidationBatchStats(rows.size(), valid, invalid, risky, unknown));
    } catch (RuntimeException exception) {
      log.warn("email_validation_batch_failed batchId={}", batchId, exception);
      jobs.failBatch(batchId, exception.getMessage());
    }
  }

  private ValidationStatus validateItem(
      EmailValidationItem item, Map<String, DomainValidationSnapshot> domainSnapshots) {
    Optional<NormalizedEmail> normalized = normalizer.normalize(item.getOriginalEmail());
    item.setSyntaxValid(normalized.isPresent());
    if (normalized.isEmpty()) {
      item.setStatus(ValidationStatus.INVALID);
      item.setReason(ValidationReason.SYNTAX_INVALID);
      item.setScore(0);
      item.setValidatedAt(Instant.now());
      return item.getStatus();
    }

    NormalizedEmail email = normalized.get();
    item.setNormalizedEmail(email.value());
    item.setDomain(email.domain());

    Optional<EmailValidationCache> cached = cache.get(email.value());
    ValidationDecision decision;
    if (cached.isPresent()) {
      decision = fromCache(cached.get());
    } else {
      DomainValidationSnapshot domain =
          domainSnapshots.computeIfAbsent(email.domain(), domains::get);
      decision = engine.validate(email, domain);
      cache.put(email.value(), decision);
    }

    apply(item, decision);
    return decision.status();
  }

  @Transactional
  protected void saveBatch(List<EmailValidationItem> rows) {
    items.saveAll(rows);
  }

  private void apply(EmailValidationItem item, ValidationDecision decision) {
    item.setStatus(decision.status());
    item.setReason(decision.reason());
    item.setScore(decision.score());
    item.setDomainRoutable(decision.domainRoutable());
    item.setMxFound(decision.mxFound());
    item.setDisposable(decision.disposable());
    item.setRoleAddress(decision.roleAddress());
    item.setCatchAll(decision.catchAll());
    item.setSmtpStatus(decision.smtpStatus());
    item.setSmtpCode(decision.smtpCode());
    item.setDiagnostic(decision.diagnostic());
    item.setValidatedAt(Instant.now());
  }

  private ValidationDecision fromCache(EmailValidationCache row) {
    return new ValidationDecision(
        row.getStatus(),
        row.getReason(),
        row.getScore(),
        row.isDomainRoutable(),
        row.isMxFound(),
        row.isDisposable(),
        row.isRoleAddress(),
        row.getCatchAll(),
        row.getSmtpStatus(),
        row.getSmtpCode(),
        row.getDiagnostic());
  }

  private void markInternalError(EmailValidationItem item, RuntimeException exception) {
    item.setStatus(ValidationStatus.UNKNOWN);
    item.setReason(ValidationReason.VALIDATION_INTERNAL_ERROR);
    item.setScore(0);
    item.setSmtpStatus(SmtpProbeStatus.UNAVAILABLE);
    String message = exception.getMessage();
    item.setDiagnostic(
        message == null
            ? "Validation failed"
            : message.length() <= 1000 ? message : message.substring(0, 1000));
    item.setValidatedAt(Instant.now());
  }
}

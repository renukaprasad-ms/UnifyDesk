package com.messaging.emailprovider.suppression;

import com.messaging.emailprovider.common.exception.ConflictException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuppressionService {

  private final EmailSuppressionRepository repository;

  public SuppressionService(EmailSuppressionRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public boolean isSuppressed(Long companyId, String address) {
    return repository.findActive(companyId, normalize(address), Instant.now()).isPresent();
  }

  @Transactional
  public EmailSuppression add(
      Long companyId, String address, SuppressionReason reason, Instant expiresAt) {
    String normalized = normalize(address);
    if (repository.findActive(companyId, normalized, Instant.now()).isPresent()) {
      throw new ConflictException("Recipient is already suppressed");
    }
    return create(companyId, normalized, reason, expiresAt);
  }

  @Transactional
  public EmailSuppression suppressIfAbsent(
      Long companyId, String address, SuppressionReason reason) {
    String normalized = normalize(address);
    return repository
        .findActive(companyId, normalized, Instant.now())
        .orElseGet(() -> create(companyId, normalized, reason, null));
  }

  @Transactional(readOnly = true)
  public List<EmailSuppression> list(Long companyId) {
    return repository.findAllByCompanyIdAndActiveTrueOrderByCreatedAtDesc(companyId);
  }

  @Transactional
  public void remove(Long companyId, Long id) {
    EmailSuppression suppression =
        repository
            .findByIdAndCompanyId(id, companyId)
            .orElseThrow(() -> new NotFoundException("Suppression not found"));
    suppression.setActive(false);
    repository.save(suppression);
  }

  private EmailSuppression create(
      Long companyId, String address, SuppressionReason reason, Instant expiresAt) {
    EmailSuppression suppression = new EmailSuppression();
    suppression.setCompanyId(companyId);
    suppression.setRecipientAddress(address);
    suppression.setReason(reason);
    suppression.setExpiresAt(expiresAt);
    return repository.save(suppression);
  }

  private String normalize(String address) {
    return address.trim().toLowerCase();
  }
}

package com.messaging.emailprovider.suppression;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailSuppressionRepository extends JpaRepository<EmailSuppression, Long> {

  @Query(
      """
      select s from EmailSuppression s
      where s.companyId = :companyId
        and s.recipientAddress = :address
        and s.active = true
        and (s.expiresAt is null or s.expiresAt > :now)
      """)
  Optional<EmailSuppression> findActive(
      @Param("companyId") Long companyId,
      @Param("address") String address,
      @Param("now") Instant now);

  List<EmailSuppression> findAllByCompanyIdAndActiveTrueOrderByCreatedAtDesc(Long companyId);

  Optional<EmailSuppression> findByIdAndCompanyId(Long id, Long companyId);
}

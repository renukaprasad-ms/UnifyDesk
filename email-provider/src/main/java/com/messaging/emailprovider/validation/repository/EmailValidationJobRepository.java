package com.messaging.emailprovider.validation.repository;

import com.messaging.emailprovider.validation.entity.EmailValidationJob;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailValidationJobRepository extends JpaRepository<EmailValidationJob, Long> {

  Optional<EmailValidationJob> findByIdAndCompanyId(Long id, Long companyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from EmailValidationJob j where j.id = :id")
  Optional<EmailValidationJob> findLockedById(@Param("id") Long id);
}

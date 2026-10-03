package com.messaging.billing.repository;

import com.messaging.billing.entity.Invoice;
import com.messaging.billing.enums.InvoiceStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

  Optional<Invoice> findByBillingPeriodId(Long billingPeriodId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Invoice i where i.id = :id")
  Optional<Invoice> findByIdForUpdate(@Param("id") Long id);

  List<Invoice> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

  List<Invoice> findByStatus(InvoiceStatus status);
}

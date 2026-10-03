package com.messaging.billing.repository;

import com.messaging.billing.entity.Invoice;
import com.messaging.billing.enums.InvoiceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
  Optional<Invoice> findByBillingPeriodId(Long billingPeriodId);

  List<Invoice> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

  List<Invoice> findByStatus(InvoiceStatus status);
}

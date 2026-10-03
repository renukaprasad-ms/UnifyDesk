package com.messaging.billing.repository;

import com.messaging.billing.entity.InvoiceLineItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, Long> {
  List<InvoiceLineItem> findByInvoiceIdOrderByCreatedAtAsc(Long invoiceId);
}

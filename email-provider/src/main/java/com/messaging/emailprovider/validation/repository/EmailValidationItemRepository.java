package com.messaging.emailprovider.validation.repository;

import com.messaging.emailprovider.validation.entity.EmailValidationItem;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailValidationItemRepository extends JpaRepository<EmailValidationItem, Long> {

  List<EmailValidationItem> findAllByBatchIdOrderByIdAsc(Long batchId);

  Page<EmailValidationItem> findAllByJobId(Long jobId, Pageable pageable);
}

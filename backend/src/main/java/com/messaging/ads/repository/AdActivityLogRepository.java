package com.messaging.ads.repository;

import com.messaging.ads.entity.AdActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdActivityLogRepository extends JpaRepository<AdActivityLog, Long> {

  Page<AdActivityLog> findByCompanyIdOrderByCreatedAtDesc(
      Long companyId, Pageable pageable);

  Page<AdActivityLog> findByCompanyIdAndAdAccountIdOrderByCreatedAtDesc(
      Long companyId, Long adAccountId, Pageable pageable);
}

package com.messaging.ads.optimization;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdOptimizationPolicyRepository
    extends JpaRepository<AdOptimizationPolicy, Long> {

  List<AdOptimizationPolicy> findByCompanyIdOrderByUpdatedAtDesc(Long companyId);

  Optional<AdOptimizationPolicy> findByIdAndCompanyId(Long id, Long companyId);

  List<AdOptimizationPolicy> findByEnabledTrueOrderByIdAsc();
}

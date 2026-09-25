package com.messaging.ads.campaign.repository;

import com.messaging.ads.campaign.entity.CreativeVariant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreativeVariantRepository extends JpaRepository<CreativeVariant, Long> {
  List<CreativeVariant> findByCompanyIdAndCreativePackIdOrderByCreatedAtAsc(Long companyId, Long creativePackId);
  Optional<CreativeVariant> findByIdAndCompanyIdAndCreativePackId(Long id, Long companyId, Long creativePackId);
}

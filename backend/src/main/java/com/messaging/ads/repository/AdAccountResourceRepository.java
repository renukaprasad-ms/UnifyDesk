package com.messaging.ads.repository;

import com.messaging.ads.entity.AdAccountResource;
import com.messaging.ads.enums.AdAccountResourceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdAccountResourceRepository extends JpaRepository<AdAccountResource, Long> {

  List<AdAccountResource> findByCompanyIdOrderByAdAccountIdAscResourceTypeAscNameAsc(
      Long companyId);

  List<AdAccountResource> findByCompanyIdAndAdAccountIdOrderByResourceTypeAscNameAsc(
      Long companyId, Long adAccountId);

  List<AdAccountResource> findByCompanyIdAndAdAccountId(
      Long companyId, Long adAccountId);

  Optional<AdAccountResource>
      findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
          Long companyId,
          Long adAccountId,
          AdAccountResourceType resourceType,
          String externalResourceId);
}

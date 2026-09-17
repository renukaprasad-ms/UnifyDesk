package com.messaging.billing.repository;

import com.messaging.billing.entity.EntitlementDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntitlementDefinitionRepository extends JpaRepository<EntitlementDefinition, Long> {
  Optional<EntitlementDefinition> findByKey(String key);
  List<EntitlementDefinition> findByActiveTrueOrderByCategoryAscNameAsc();
}

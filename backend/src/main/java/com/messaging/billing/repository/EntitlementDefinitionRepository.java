package com.messaging.billing.repository;

import com.messaging.billing.entity.EntitlementDefinition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntitlementDefinitionRepository
    extends JpaRepository<EntitlementDefinition, String> {

  List<EntitlementDefinition> findByActiveTrueOrderByCategoryAscLabelAsc();
}

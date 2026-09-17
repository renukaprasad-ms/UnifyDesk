package com.messaging.company.repository;

import com.messaging.company.entity.PermissionDefinition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionDefinitionRepository extends JpaRepository<PermissionDefinition, String> {

  List<PermissionDefinition> findAllByOrderByResourceAscActionAsc();
}

package com.messaging.emailprovider.product.repo;

import com.messaging.emailprovider.product.model.EmailProject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailProjectRepository extends JpaRepository<EmailProject, Long> {
  List<EmailProject> findAllByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}

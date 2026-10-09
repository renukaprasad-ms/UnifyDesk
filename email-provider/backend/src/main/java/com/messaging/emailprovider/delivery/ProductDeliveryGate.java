package com.messaging.emailprovider.delivery;

import com.messaging.emailprovider.product.model.EmailOrganization;
import com.messaging.emailprovider.product.model.EmailProject;
import com.messaging.emailprovider.product.repo.EmailOrganizationRepository;
import com.messaging.emailprovider.product.repo.EmailProjectRepository;
import org.springframework.stereotype.Component;

/** Fail closed for registered product projects; legacy private-service tenants are separate. */
@Component
public class ProductDeliveryGate {
  private final EmailProjectRepository projects;
  private final EmailOrganizationRepository organizations;

  public ProductDeliveryGate(EmailProjectRepository projects,
      EmailOrganizationRepository organizations) {
    this.projects = projects;
    this.organizations = organizations;
  }

  public boolean permitted(Long projectId) {
    EmailProject project = projects.findById(projectId).orElse(null);
    if (project == null) {
      // Legacy /api/v1/companies endpoints use the separate internal-only token.
      return true;
    }
    if (!project.isSendingEnabled() || !"ACTIVE".equals(project.getStatus())) return false;
    EmailOrganization org = organizations.findById(project.getOrganizationId()).orElse(null);
    return org != null && "ACTIVE".equals(org.getStatus());
  }
}

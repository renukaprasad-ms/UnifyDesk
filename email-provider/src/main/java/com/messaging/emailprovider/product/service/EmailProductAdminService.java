package com.messaging.emailprovider.product.service;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.product.api.ProductRequests;
import com.messaging.emailprovider.product.api.ProductResponses;
import com.messaging.emailprovider.product.auth.ApiKeyCrypto;
import com.messaging.emailprovider.product.auth.ApiKeyScope;
import com.messaging.emailprovider.product.model.EmailApiKey;
import com.messaging.emailprovider.product.model.EmailOrganization;
import com.messaging.emailprovider.product.model.EmailProject;
import com.messaging.emailprovider.product.repo.EmailApiKeyRepository;
import com.messaging.emailprovider.product.repo.EmailOrganizationRepository;
import com.messaging.emailprovider.product.repo.EmailProjectRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailProductAdminService {
  private final EmailOrganizationRepository organizations;
  private final EmailProjectRepository projects;
  private final EmailApiKeyRepository keys;

  public EmailProductAdminService(
      EmailOrganizationRepository organizations,
      EmailProjectRepository projects,
      EmailApiKeyRepository keys) {
    this.organizations = organizations;
    this.projects = projects;
    this.keys = keys;
  }

  @Transactional
  public ProductResponses.Organization createOrganization(ProductRequests.NewOrganization req) {
    EmailOrganization org = new EmailOrganization();
    org.setName(req.name().trim());
    org.setContactEmail(req.contactEmail().trim());
    return organization(organizations.save(org));
  }

  @Transactional
  public ProductResponses.Project createProject(Long orgId, ProductRequests.NewProject req) {
    requireOrg(orgId);
    EmailProject project = new EmailProject();
    project.setOrganizationId(orgId);
    project.setName(req.name().trim());
    return project(projects.save(project));
  }

  @Transactional(readOnly = true)
  public List<ProductResponses.Project> projects(Long orgId) {
    requireOrg(orgId);
    return projects.findAllByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
        .map(this::project).toList();
  }

  @Transactional
  public ProductResponses.IssuedKey issue(Long projectId, ProductRequests.NewKey req) {
    requireProject(projectId);
    if (req.scopes() == null || req.scopes().isEmpty()) {
      throw new BadRequestException("At least one API key scope is required");
    }
    String prefix = ApiKeyCrypto.random(9);
    String secret = "ude_live_" + prefix + "_" + ApiKeyCrypto.random(32);
    EmailApiKey key = new EmailApiKey();
    key.setProjectId(projectId);
    key.setName(req.name().trim());
    key.setLookupPrefix(prefix);
    key.setSecretHash(ApiKeyCrypto.hash(secret));
    key.setScopes(req.scopes().stream().map(ApiKeyScope::name)
        .sorted(Comparator.naturalOrder()).collect(Collectors.joining(",")));
    key.setExpiresAt(req.expiresAt());
    key = keys.save(key);
    return new ProductResponses.IssuedKey(key(key), secret);
  }

  @Transactional(readOnly = true)
  public List<ProductResponses.ApiKey> keys(Long projectId) {
    requireProject(projectId);
    return keys.findAllByProjectIdOrderByCreatedAtDesc(projectId).stream()
        .map(this::key).toList();
  }

  @Transactional
  public void revoke(Long projectId, Long keyId) {
    requireProject(projectId);
    EmailApiKey key = keys.findByIdAndProjectId(keyId, projectId)
        .orElseThrow(() -> new NotFoundException("API key not found"));
    key.setRevokedAt(Instant.now());
    keys.save(key);
  }

  @Transactional
  public ProductResponses.Project setSending(Long projectId, boolean enabled) {
    EmailProject project = requireProject(projectId);
    if (enabled && !"ACTIVE".equals(requireOrg(project.getOrganizationId()).getStatus())) {
      throw new BadRequestException("Organization is not active");
    }
    project.setSendingEnabled(enabled);
    return project(projects.save(project));
  }

  private EmailProject requireProject(Long id) {
    return projects.findById(id)
        .orElseThrow(() -> new NotFoundException("Email project not found"));
  }

  private EmailOrganization requireOrg(Long id) {
    return organizations.findById(id)
        .orElseThrow(() -> new NotFoundException("Email organization not found"));
  }

  private ProductResponses.Organization organization(EmailOrganization o) {
    return new ProductResponses.Organization(o.getId().toString(), o.getName(),
        o.getContactEmail(), o.getStatus());
  }

  private ProductResponses.Project project(EmailProject p) {
    return new ProductResponses.Project(p.getId().toString(),
        p.getOrganizationId().toString(), p.getName(), p.getStatus(), p.isSendingEnabled());
  }

  private ProductResponses.ApiKey key(EmailApiKey k) {
    return new ProductResponses.ApiKey(k.getId().toString(), k.getProjectId().toString(),
        k.getName(), k.getLookupPrefix(), k.getScopes(), k.getExpiresAt(),
        k.getRevokedAt(), k.getCreatedAt());
  }
}

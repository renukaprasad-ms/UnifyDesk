package com.messaging.admin.service;

import com.messaging.admin.dto.*;
import com.messaging.admin.enums.PlatformPermission;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.security.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformCompanyManagementService {
  private final CompanyRepository companyRepository;
  private final CurrentUserService currentUserService;
  private final PlatformAdminAuthorizationService authorizationService;

  @Transactional(readOnly = true)
  public Page<PlatformCompanyResponse> list(String search, Pageable pageable) {
    Long actor = currentUserService.currentUserId();
    authorizationService.requirePermission(actor, PlatformPermission.COMPANY_VIEW);
    Page<Company> page =
        search == null || search.isBlank()
            ? companyRepository.findAll(pageable)
            : companyRepository.findByNameContainingIgnoreCaseOrBusinessEmailContainingIgnoreCase(
                search.trim(), search.trim(), pageable);
    return page.map(this::response);
  }

  @Transactional
  public PlatformCompanyResponse updateStatus(Long id, UpdateCompanyStatusRequest request) {
    Long actor = currentUserService.currentUserId();
    authorizationService.requirePermission(actor, PlatformPermission.COMPANY_SUSPEND);
    Company company =
        companyRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Company not found"));
    company.setStatus(request.status());
    return response(company);
  }

  private PlatformCompanyResponse response(Company c) {
    return new PlatformCompanyResponse(
        c.getId().toString(),
        c.getName(),
        c.getSlug(),
        c.getBusinessEmail(),
        c.getCountry(),
        c.getTimezone(),
        c.getStatus());
  }
}

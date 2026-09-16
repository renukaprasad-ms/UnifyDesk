package com.messaging.admin.service;

import com.messaging.admin.dto.AdminCompanyAssignmentResponse;
import com.messaging.admin.dto.AssignAdminCompanyRequest;
import com.messaging.admin.entity.AdminCompanyAssignment;
import com.messaging.admin.repository.AdminCompanyAssignmentRepository;
import com.messaging.admin.enums.PlatformPermission;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.dto.CompanyResponse;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.security.service.CurrentUserService;
import com.messaging.user.entity.User;
import com.messaging.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCompanyService {

  private final PlatformAdminAuthorizationService adminAuthorizationService;
  private final AdminCompanyAssignmentRepository assignmentRepository;
  private final CompanyRepository companyRepository;
  private final UserRepository userRepository;
  private final CurrentUserService currentUserService;

  @Transactional(readOnly = true)
  public List<CompanyResponse> visibleCompanies() {
    Long userId = currentUserService.currentUserId();
    adminAuthorizationService.requirePermission(userId, PlatformPermission.COMPANY_VIEW);
    if (adminAuthorizationService.isSuperAdmin(userId)) {
      return companyRepository.findAll().stream()
          .map(this::toCompanyResponse)
          .toList();
    }
    return assignmentRepository.findByAdminUserId(userId).stream()
        .map(AdminCompanyAssignment::getCompany)
        .map(this::toCompanyResponse)
        .toList();
  }

  @Transactional
  public AdminCompanyAssignmentResponse assignCompany(AssignAdminCompanyRequest request) {
    Long actorId = currentUserService.currentUserId();
    adminAuthorizationService.requireSuperAdmin(actorId);
    Long adminUserId = parseId(request.adminUserId());
    Long companyId = parseId(request.companyId());
    User adminUser =
        userRepository
            .findById(adminUserId)
            .orElseThrow(() -> new NotFoundException("Admin user not found"));
    User actor =
        userRepository.findById(actorId).orElseThrow(() -> new NotFoundException("User not found"));
    Company company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new NotFoundException("Company not found"));

    AdminCompanyAssignment assignment =
        assignmentRepository
            .findByAdminUserIdAndCompanyId(adminUserId, companyId)
            .orElseGet(AdminCompanyAssignment::new);
    assignment.setAdminUser(adminUser);
    assignment.setCompany(company);
    assignment.setAssignedBy(actor);
    assignmentRepository.saveAndFlush(assignment);
    return new AdminCompanyAssignmentResponse(
        adminUserId.toString(), companyId.toString(), actorId.toString());
  }

  private Long parseId(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new NotFoundException("Resource not found");
    }
  }

  private CompanyResponse toCompanyResponse(Company company) {
    return new CompanyResponse(
        company.getId().toString(),
        company.getName(),
        company.getSlug(),
        company.getBusinessEmail(),
        company.getCountry(),
        company.getTimezone(),
        company.getLogoMediaId() == null ? null : company.getLogoMediaId().toString(),
        company.getStatus(),
        null);
  }
}

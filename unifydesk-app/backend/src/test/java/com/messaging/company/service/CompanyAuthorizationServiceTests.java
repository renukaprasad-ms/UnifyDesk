package com.messaging.company.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.company.entity.Company;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyAuthorizationServiceTests {

  private static final Long USER_ID = 200L;
  private static final Long COMPANY_ID = 100L;

  @Mock private CompanyPermissionService permissionService;
  @Mock private CompanyIamPermissionService iamPermissionService;
  @Mock private CompanyMemberRepository memberRepository;
  @Mock private CompanyRepository companyRepository;
  @Mock private PlatformAdminAuthorizationService platformAdminAuthorizationService;

  private CompanyAuthorizationService service;

  @BeforeEach
  void setUp() {
    service =
        new CompanyAuthorizationService(
            permissionService,
            iamPermissionService,
            memberRepository,
            companyRepository,
            platformAdminAuthorizationService);
  }

  @Test
  void suspendedCompanyBlocksNormalMemberPermissionChecks() {
    Company company = new Company();
    company.setId(COMPANY_ID);
    company.setStatus(CompanyStatus.SUSPENDED);
    when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
    when(memberRepository.existsByCompanyIdAndUserIdAndStatus(
            COMPANY_ID, USER_ID, CompanyMemberStatus.ACTIVE))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                service.requirePermission(
                    USER_ID, COMPANY_ID, CompanyPermissionKeys.COMPANY_READ))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("Company permission denied");
  }

  @Test
  void deletionPendingCompanyBlocksMembershipAccess() {
    Company company = new Company();
    company.setId(COMPANY_ID);
    company.setStatus(CompanyStatus.DELETION_PENDING);
    when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

    assertThatThrownBy(() -> service.requireActiveMembership(USER_ID, COMPANY_ID))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("pending deletion");
  }
}

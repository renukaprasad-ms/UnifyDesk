package com.messaging.company.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.messaging.common.exception.ForbiddenException;
import com.messaging.company.dto.CreateCompanyRoleRequest;
import com.messaging.company.entity.Company;
import com.messaging.company.entity.PermissionDefinition;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.repository.CompanyCustomRolePermissionRepository;
import com.messaging.company.repository.CompanyCustomRoleRepository;
import com.messaging.company.repository.CompanyInvitationRepository;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyMemberRoleAssignmentRepository;
import com.messaging.company.repository.PermissionDefinitionRepository;
import com.messaging.lifecycle.service.DeletionQueueService;
import com.messaging.security.service.CurrentUserService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyRoleManagementServiceTests {

  private static final Long COMPANY_ID = 100L;
  private static final Long ACTOR_ID = 200L;

  @Mock private CompanyService companyService;
  @Mock private CompanyAuthorizationService authorizationService;
  @Mock private CompanyIamPermissionService iamPermissionService;
  @Mock private CompanyAccessRealtimeService accessRealtimeService;
  @Mock private SystemPermissionCatalogService permissionCatalogService;
  @Mock private CompanySystemRoleService companySystemRoleService;
  @Mock private CompanyMemberRepository memberRepository;
  @Mock private CompanyInvitationRepository invitationRepository;
  @Mock private PermissionDefinitionRepository permissionDefinitionRepository;
  @Mock private CompanyCustomRoleRepository roleRepository;
  @Mock private CompanyCustomRolePermissionRepository rolePermissionRepository;
  @Mock private CompanyMemberRoleAssignmentRepository assignmentRepository;
  @Mock private CurrentUserService currentUserService;
  @Mock private DeletionQueueService deletionQueueService;

  private CompanyRoleManagementService service;

  @BeforeEach
  void setUp() {
    service =
        new CompanyRoleManagementService(
            companyService,
            authorizationService,
            iamPermissionService,
            accessRealtimeService,
            permissionCatalogService,
            companySystemRoleService,
            memberRepository,
            invitationRepository,
            permissionDefinitionRepository,
            roleRepository,
            rolePermissionRepository,
            assignmentRepository,
            currentUserService,
            deletionQueueService);
  }

  @Test
  void userCannotCreateRoleWithPermissionTheyDoNotPossess() {
    PermissionDefinition billingCardAdd = new PermissionDefinition();
    billingCardAdd.setKey(CompanyPermissionKeys.BILLING_CARD_ADD);
    billingCardAdd.setResource("billing.card");
    billingCardAdd.setAction("add");
    billingCardAdd.setDescription("Add payment methods");
    billingCardAdd.setAssignable(true);

    when(currentUserService.currentUserId()).thenReturn(ACTOR_ID);
    when(companyService.getCompany(COMPANY_ID)).thenReturn(new Company());
    when(roleRepository.existsByCompanyIdAndNameIgnoreCaseAndDeletedAtIsNull(
            COMPANY_ID, "Billing Admin"))
        .thenReturn(false);
    when(permissionDefinitionRepository.findAllById(any())).thenReturn(List.of(billingCardAdd));
    when(iamPermissionService.canDelegate(
            ACTOR_ID, COMPANY_ID, Set.of(CompanyPermissionKeys.BILLING_CARD_ADD)))
        .thenReturn(false);

    CreateCompanyRoleRequest request =
        new CreateCompanyRoleRequest(
            "Billing Admin", null, Set.of(CompanyPermissionKeys.BILLING_CARD_ADD));

    assertThatThrownBy(() -> service.createRole(COMPANY_ID, request))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("cannot grant");
  }
}

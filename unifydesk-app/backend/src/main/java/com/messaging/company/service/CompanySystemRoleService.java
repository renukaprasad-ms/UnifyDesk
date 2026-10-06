package com.messaging.company.service;

import com.messaging.common.exception.NotFoundException;
import com.messaging.common.id.IdGenerator;
import com.messaging.company.enums.CompanyRole;
import com.messaging.company.iam.CompanyPermissionKeys;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanySystemRoleService {

  public static final String OWNER_ROLE_NAME = "Owner";
  public static final String MANAGER_ROLE_NAME = "Manager";
  public static final String MEMBER_ROLE_NAME = "Member";

  private final JdbcTemplate jdbcTemplate;
  private final SystemPermissionCatalogService permissionCatalogService;

  @Transactional
  public void ensureDefaults(Long companyId) {
    List<Long> creators =
        jdbcTemplate.queryForList(
            "select created_by_user_id from companies where id = ?",
            Long.class,
            companyId);
    if (creators.isEmpty()) {
      throw new NotFoundException("Company not found");
    }
    ensureDefaults(companyId, creators.getFirst());
  }

  @Transactional
  public void ensureDefaults(Long companyId, Long createdByUserId) {
    permissionCatalogService.ensureCatalog();

    Long ownerRoleId =
        ensureRole(
            companyId,
            createdByUserId,
            OWNER_ROLE_NAME,
            "System role representing the company owner. Owner access is always full.");
    Long managerRoleId =
        ensureRole(
            companyId,
            createdByUserId,
            MANAGER_ROLE_NAME,
            "System role showing the default access granted to company managers.");
    Long memberRoleId =
        ensureRole(
            companyId,
            createdByUserId,
            MEMBER_ROLE_NAME,
            "System role showing the default access granted to company members.");

    List<String> allPermissions =
        jdbcTemplate.queryForList(
            "select permission_key from permissions order by permission_key", String.class);
    syncPermissions(ownerRoleId, Set.copyOf(allPermissions));
    syncPermissions(managerRoleId, CompanyPermissionKeys.defaultsFor(CompanyRole.MANAGER));
    syncPermissions(memberRoleId, CompanyPermissionKeys.defaultsFor(CompanyRole.MEMBER));
  }

  private Long ensureRole(
      Long companyId, Long createdByUserId, String name, String description) {
    jdbcTemplate.update(
        """
        insert into company_custom_roles
          (id, company_id, name, description, system_role, created_by_user_id,
           created_at, updated_at, deleted_at, deleted_by_user_id)
        values (?, ?, ?, ?, true, ?, now(), now(), null, null)
        on conflict do nothing
        """,
        IdGenerator.nextId(),
        companyId,
        name,
        description,
        createdByUserId);

    List<Long> roles =
        jdbcTemplate.queryForList(
            """
            select id
            from company_custom_roles
            where company_id = ?
              and lower(name) = lower(?)
              and deleted_at is null
            limit 1
            """,
            Long.class,
            companyId,
            name);

    if (roles.isEmpty()) {
      throw new IllegalStateException("Unable to create system company role " + name);
    }

    Long roleId = roles.getFirst();
    jdbcTemplate.update(
        """
        update company_custom_roles
        set system_role = true,
            description = ?,
            updated_at = now()
        where id = ?
        """,
        description,
        roleId);
    return roleId;
  }

  private void syncPermissions(Long roleId, Set<String> permissions) {
    Set<String> existing =
        new HashSet<>(
            jdbcTemplate.queryForList(
                "select permission_key from company_custom_role_permissions where role_id = ?",
                String.class,
                roleId));

    for (String stale : existing) {
      if (!permissions.contains(stale)) {
        jdbcTemplate.update(
            "delete from company_custom_role_permissions where role_id = ? and permission_key = ?",
            roleId,
            stale);
      }
    }

    for (String permission : permissions) {
      if (existing.contains(permission)) continue;
      jdbcTemplate.update(
          """
          insert into company_custom_role_permissions
            (id, role_id, permission_key)
          values (?, ?, ?)
          on conflict (role_id, permission_key) do nothing
          """,
          IdGenerator.nextId(),
          roleId,
          permission);
    }
  }
}

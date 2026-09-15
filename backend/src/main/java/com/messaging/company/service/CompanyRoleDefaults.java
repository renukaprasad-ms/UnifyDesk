package com.messaging.company.service;

import com.messaging.company.enums.CompanyPermission;
import com.messaging.company.enums.CompanyRole;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CompanyRoleDefaults {

  private final EnumMap<CompanyRole, EnumSet<CompanyPermission>> defaults =
      new EnumMap<>(CompanyRole.class);

  public CompanyRoleDefaults() {
    defaults.put(CompanyRole.OWNER, EnumSet.allOf(CompanyPermission.class));
    defaults.put(
        CompanyRole.MANAGER,
        EnumSet.of(
            CompanyPermission.COMPANY_VIEW,
            CompanyPermission.MEMBER_INVITE,
            CompanyPermission.MESSAGE_VIEW,
            CompanyPermission.MESSAGE_SEND,
            CompanyPermission.BROADCAST_CREATE,
            CompanyPermission.TEMPLATE_CREATE,
            CompanyPermission.MEDIA_UPLOAD,
            CompanyPermission.SETTINGS_VIEW));
    defaults.put(
        CompanyRole.MEMBER,
        EnumSet.of(
            CompanyPermission.COMPANY_VIEW,
            CompanyPermission.MESSAGE_VIEW,
            CompanyPermission.MESSAGE_SEND,
            CompanyPermission.MEDIA_UPLOAD));
  }

  public Set<CompanyPermission> permissionsFor(CompanyRole role) {
    return EnumSet.copyOf(defaults.getOrDefault(role, EnumSet.noneOf(CompanyPermission.class)));
  }
}

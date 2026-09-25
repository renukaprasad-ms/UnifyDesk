package com.messaging.admin.repository;

import com.messaging.admin.entity.PlatformAdminPermission;
import com.messaging.admin.enums.PlatformPermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAdminPermissionRepository
    extends JpaRepository<PlatformAdminPermission, Long> {
  boolean existsByPlatformAdminIdAndPermission(Long platformAdminId, PlatformPermission permission);

  List<PlatformAdminPermission> findByPlatformAdminId(Long platformAdminId);

  void deleteByPlatformAdminId(Long platformAdminId);
}

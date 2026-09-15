package com.messaging.admin.repository;

import com.messaging.admin.entity.PlatformAdmin;
import com.messaging.admin.enums.PlatformAdminStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, Long> {

  Optional<PlatformAdmin> findByUserIdAndStatus(Long userId, PlatformAdminStatus status);
}

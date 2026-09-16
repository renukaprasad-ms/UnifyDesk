package com.messaging.admin.repository;

import com.messaging.admin.entity.PlatformAdmin;
import com.messaging.admin.enums.PlatformAdminStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, Long> {

  Optional<PlatformAdmin> findByUserIdAndStatus(Long userId, PlatformAdminStatus status);
  Optional<PlatformAdmin> findByUserId(Long userId);
  Page<PlatformAdmin> findByUserEmailContainingIgnoreCase(String email, Pageable pageable);
}

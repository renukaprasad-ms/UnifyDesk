package com.messaging.managedsms.control.repository;

import com.messaging.managedsms.control.entity.ManagedSmsComplianceAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedSmsComplianceActionRepository
    extends JpaRepository<ManagedSmsComplianceAction, Long> {}

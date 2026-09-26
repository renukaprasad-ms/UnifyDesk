package com.messaging.managedemail.control.repository;

import com.messaging.managedemail.control.entity.ManagedEmailAdminAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedEmailAdminActionRepository
    extends JpaRepository<ManagedEmailAdminAction, Long> {}

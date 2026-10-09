package com.messaging.emailprovider.product.repo;

import com.messaging.emailprovider.product.model.EmailOrganization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOrganizationRepository extends JpaRepository<EmailOrganization, Long> {}

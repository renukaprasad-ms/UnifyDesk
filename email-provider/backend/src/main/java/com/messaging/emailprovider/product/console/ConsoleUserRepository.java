package com.messaging.emailprovider.product.console;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsoleUserRepository extends JpaRepository<ConsoleUser, Long> {
  Optional<ConsoleUser> findByOrganizationIdAndEmail(Long organizationId, String email);
}

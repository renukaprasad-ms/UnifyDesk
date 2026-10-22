package com.messaging.emailprovider.product.console;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsoleUserRepository extends JpaRepository<ConsoleUser, Long> {
  Optional<ConsoleUser> findByOrganizationIdAndEmail(Long organizationId, String email);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select u from ConsoleUser u where u.organizationId=:organizationId and u.email=:email")
  Optional<ConsoleUser> findLockedByOrganizationIdAndEmail(Long organizationId, String email);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select u from ConsoleUser u where u.id=:id")
  Optional<ConsoleUser> findLockedById(Long id);
}

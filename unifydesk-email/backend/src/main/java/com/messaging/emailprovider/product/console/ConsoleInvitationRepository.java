package com.messaging.emailprovider.product.console;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsoleInvitationRepository extends JpaRepository<ConsoleInvitation, Long> {
  Optional<ConsoleInvitation> findByTokenHash(String hash);
}

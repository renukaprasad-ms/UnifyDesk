package com.messaging.emailprovider.product.console;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsoleSessionRepository extends JpaRepository<ConsoleSession, Long> {
  Optional<ConsoleSession> findByTokenHash(String hash);
}

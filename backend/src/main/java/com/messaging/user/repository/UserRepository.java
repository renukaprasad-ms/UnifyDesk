package com.messaging.user.repository;

import com.messaging.user.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmailOrUsername(String email, String username);

  Optional<User> findByEmail(String email);

  Page<User>
      findByNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrUsernameContainingIgnoreCase(
          String name, String email, String username, Pageable pageable);

  boolean existsByEmailAndIdNot(String email, Long id);

  boolean existsByUsernameAndIdNot(String username, Long id);
}

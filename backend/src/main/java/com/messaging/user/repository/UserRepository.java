package com.messaging.user.repository;

import com.messaging.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmailOrUsername(String email, String username);

  Optional<User> findByEmail(String email);
}

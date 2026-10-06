package com.messaging.user.repository;

import com.messaging.user.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> findByIdForUpdate(@Param("id") Long id);

  Optional<User> findByEmailOrUsername(String email, String username);

  Optional<User> findByEmail(String email);

  Optional<User> findByEmailIgnoreCase(String email);

  Page<User>
      findByNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrUsernameContainingIgnoreCase(
          String name, String email, String username, Pageable pageable);

  boolean existsByEmailAndIdNot(String email, Long id);

  boolean existsByUsernameAndIdNot(String username, Long id);
}

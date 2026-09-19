package com.messaging.asyncjob.repository;

import com.messaging.asyncjob.entity.AsyncJob;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsyncJobRepository extends JpaRepository<AsyncJob, Long> {
  Optional<AsyncJob> findByIdAndUserId(Long id, Long userId);
}

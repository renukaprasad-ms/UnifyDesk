package com.messaging.company.repository;

import com.messaging.company.entity.Company;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

  boolean existsBySlug(String slug);

  Optional<Company> findBySlug(String slug);

  Optional<Company> findByLogoMediaId(Long logoMediaId);

  Page<Company> findByNameContainingIgnoreCaseOrBusinessEmailContainingIgnoreCase(
      String name, String email, Pageable pageable);
}

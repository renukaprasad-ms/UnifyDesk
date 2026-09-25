package com.messaging.company.repository;

import com.messaging.company.entity.CompanyAccessProfile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyAccessProfileRepository extends JpaRepository<CompanyAccessProfile, Long> {

  List<CompanyAccessProfile> findByCompanyId(Long companyId);
}

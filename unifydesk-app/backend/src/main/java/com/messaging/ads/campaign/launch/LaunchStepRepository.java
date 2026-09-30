package com.messaging.ads.campaign.launch;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LaunchStepRepository extends JpaRepository<LaunchStep, Long> {

  List<LaunchStep> findByCompanyIdAndLaunchRunIdOrderBySequenceAscIdAsc(
      Long companyId, Long launchRunId);

  Optional<LaunchStep> findByIdAndCompanyIdAndLaunchRunId(
      Long id, Long companyId, Long launchRunId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select step
        from LaunchStep step
       where step.id = :id
         and step.companyId = :companyId
         and step.launchRunId = :launchRunId
      """)
  Optional<LaunchStep> findByIdAndCompanyIdAndLaunchRunIdForUpdate(
      @Param("id") Long id,
      @Param("companyId") Long companyId,
      @Param("launchRunId") Long launchRunId);
}

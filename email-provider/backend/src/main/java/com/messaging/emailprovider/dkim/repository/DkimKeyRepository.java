package com.messaging.emailprovider.dkim.repository;

import com.messaging.emailprovider.dkim.entity.DkimKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DkimKeyRepository extends JpaRepository<DkimKey, Long> {

  Optional<DkimKey> findFirstBySendingDomainIdAndActiveTrueOrderByCreatedAtDesc(Long sendingDomainId);
}

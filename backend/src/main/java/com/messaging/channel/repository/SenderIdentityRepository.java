package com.messaging.channel.repository;

import com.messaging.channel.entity.SenderIdentity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SenderIdentityRepository extends JpaRepository<SenderIdentity, Long> {

  List<SenderIdentity> findByConnectionIdOrderByCreatedAtAsc(Long connectionId);

  Optional<SenderIdentity> findByIdAndConnectionId(Long id, Long connectionId);

  Optional<SenderIdentity> findFirstByConnectionIdAndDefaultIdentityTrue(Long connectionId);

  long countByConnectionCompanyId(Long companyId);
}

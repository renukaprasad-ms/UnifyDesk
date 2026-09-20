package com.messaging.channel.microsoft.repository;

import com.messaging.channel.microsoft.entity.MicrosoftSubscription;
import com.messaging.channel.microsoft.enums.MicrosoftSubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MicrosoftSubscriptionRepository
    extends JpaRepository<MicrosoftSubscription, Long> {

  @EntityGraph(attributePaths = "connection")
  Optional<MicrosoftSubscription> findByConnectionId(Long connectionId);

  @EntityGraph(attributePaths = "connection")
  Optional<MicrosoftSubscription> findByExternalSubscriptionId(String externalSubscriptionId);

  @EntityGraph(attributePaths = "connection")
  List<MicrosoftSubscription> findByExternalSubscriptionIdIn(List<String> externalSubscriptionIds);

  @EntityGraph(attributePaths = "connection")
  List<MicrosoftSubscription> findByStatusAndExpirationAtBefore(
      MicrosoftSubscriptionStatus status, Instant expirationAt);
}

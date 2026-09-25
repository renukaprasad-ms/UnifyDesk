package com.messaging.broadcast.repository;

import com.messaging.broadcast.entity.BroadcastSuppression;
import com.messaging.channel.enums.ChannelType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BroadcastSuppressionRepository extends JpaRepository<BroadcastSuppression, Long> {
  @Query(
      """
      select suppression.normalizedDestination
      from BroadcastSuppression suppression
      where suppression.companyId = :companyId
        and suppression.channelType = :channelType
        and suppression.active = true
        and suppression.normalizedDestination in :destinations
      """)
  List<String> findActiveDestinations(
      @Param("companyId") Long companyId,
      @Param("channelType") ChannelType channelType,
      @Param("destinations") Collection<String> destinations);

  Optional<BroadcastSuppression> findByCompanyIdAndChannelTypeAndNormalizedDestination(
      Long companyId, ChannelType channelType, String normalizedDestination);
}

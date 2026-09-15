package com.messaging.billing.repository;

import com.messaging.billing.entity.RateCard;
import com.messaging.billing.enums.RateCardStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateCardRepository extends JpaRepository<RateCard, Long> {

  List<RateCard> findByPlanIdAndStatus(Long planId, RateCardStatus status);
}

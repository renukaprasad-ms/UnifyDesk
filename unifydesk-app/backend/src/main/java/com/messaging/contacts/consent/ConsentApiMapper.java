package com.messaging.contacts.consent;

import com.messaging.common.exception.BadRequestException;
import com.messaging.contacts.consent.ConsentApiModel.EligibilityResponse;
import com.messaging.contacts.consent.ConsentApiModel.PreferenceResponse;
import com.messaging.contacts.consent.ConsentApiModel.SuppressionResponse;
import com.messaging.contacts.consent.ConsentTypes.EligibilityResult;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSnapshot;
import com.messaging.contacts.consent.ConsentTypes.SuppressionSnapshot;
import com.messaging.contacts.consent.ConsentTypes.SuppressionState;
import org.springframework.stereotype.Component;

@Component
public class ConsentApiMapper {

  public PreferenceResponse toApi(PreferenceSnapshot snapshot) {
    return new PreferenceResponse(
        Long.toString(snapshot.id()),
        snapshot.channel(),
        snapshot.purpose(),
        snapshot.state(),
        id(snapshot.identityId()),
        snapshot.source(),
        snapshot.sourceRef(),
        snapshot.occurredAt(),
        snapshot.updatedAt(),
        null,
        snapshot.version());
  }

  public SuppressionResponse toApi(
      SuppressionSnapshot snapshot, boolean liftable) {
    return new SuppressionResponse(
        Long.toString(snapshot.id()),
        snapshot.channel(),
        id(snapshot.identityId()),
        snapshot.reasonCode(),
        snapshot.source(),
        snapshot.sourceRef(),
        snapshot.state().name(),
        snapshot.suppressedAt(),
        snapshot.liftedAt(),
        liftable,
        snapshot.state() == SuppressionState.ACTIVE,
        snapshot.version());
  }

  public EligibilityResponse toApi(EligibilityResult result) {
    return new EligibilityResponse(
        result.decision(),
        result.reason(),
        id(result.identityId()),
        result.normalizedDestination(),
        result.policyVersion(),
        result.evaluatedAt());
  }

  public Long parseOptionalId(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      long parsed = Long.parseLong(value);
      if (parsed <= 0) throw invalid();
      return parsed;
    } catch (NumberFormatException exception) {
      throw invalid();
    }
  }

  private String id(Long value) {
    return value == null ? null : Long.toString(value);
  }

  private BadRequestException invalid() {
    return new BadRequestException("CONTACT_FIELD_INVALID");
  }
}

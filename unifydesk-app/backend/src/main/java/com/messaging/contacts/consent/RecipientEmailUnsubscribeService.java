package com.messaging.contacts.consent;

import com.messaging.common.exception.ApiException;
import com.messaging.contacts.consent.RecipientActionTokenService.ValidatedRecipientAction;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecipientEmailUnsubscribeService {

  private final RecipientActionTokenService tokenService;
  private final ContactPreferenceService preferenceService;

  public void unsubscribe(String token) {
    ValidatedRecipientAction action = tokenService.validate(token);
    String actionRef = "email-unsubscribe:v1:" + action.actionId();
    try {
      preferenceService.setFromContactAction(
          action.companyId(),
          action.contactId(),
          action.identityId(),
          actionRef,
          "Recipient unsubscribed from marketing email",
          Instant.now(),
          "contact-action:" + action.actionId());
    } catch (ApiException exception) {
      throw new InvalidRecipientActionException();
    }
  }
}

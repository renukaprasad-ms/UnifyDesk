package com.messaging.notification.template;

import org.springframework.stereotype.Component;

@Component
public class TwoFactorSettingsOtpEmailTemplate implements OtpEmailTemplate {

  @Override
  public EmailTemplateType type() {
    return EmailTemplateType.TWO_FACTOR_SETTINGS_OTP;
  }

  @Override
  public String subject() {
    return "Confirm your security settings";
  }

  @Override
  public String title() {
    return "Confirm your security change";
  }

  @Override
  public String message() {
    return "Use this one-time code to finish updating your security settings.";
  }

  @Override
  public String securityNote() {
    return "If you did not request this change, do not share this code and review your account.";
  }
}

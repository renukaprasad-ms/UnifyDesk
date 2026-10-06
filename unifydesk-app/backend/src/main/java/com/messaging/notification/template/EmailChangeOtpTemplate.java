package com.messaging.notification.template;

import org.springframework.stereotype.Component;

@Component
public class EmailChangeOtpTemplate implements OtpEmailTemplate {

  @Override
  public EmailTemplateType type() {
    return EmailTemplateType.EMAIL_CHANGE_OTP;
  }

  @Override
  public String subject() {
    return "Confirm your new email";
  }

  @Override
  public String title() {
    return "Confirm your new email address";
  }

  @Override
  public String message() {
    return "Use the verification code below to confirm this new email address for your UnifyDesk account.";
  }

  @Override
  public String securityNote() {
    return "If you did not request this email change, do not share this code and keep your current email unchanged.";
  }
}

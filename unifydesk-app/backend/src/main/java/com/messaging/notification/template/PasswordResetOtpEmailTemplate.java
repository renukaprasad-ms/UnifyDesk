package com.messaging.notification.template;

import org.springframework.stereotype.Component;

@Component
public class PasswordResetOtpEmailTemplate implements OtpEmailTemplate {

  @Override
  public EmailTemplateType type() {
    return EmailTemplateType.PASSWORD_RESET_OTP;
  }

  @Override
  public String subject() {
    return "Reset your password";
  }

  @Override
  public String title() {
    return "Reset your password";
  }

  @Override
  public String message() {
    return "Use the code below to continue resetting your password.";
  }

  @Override
  public String securityNote() {
    return "If you did not request a password reset, you can safely ignore this email.";
  }
}

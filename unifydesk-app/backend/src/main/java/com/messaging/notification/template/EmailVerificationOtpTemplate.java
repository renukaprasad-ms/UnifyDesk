package com.messaging.notification.template;

import org.springframework.stereotype.Component;

@Component
public class EmailVerificationOtpTemplate implements OtpEmailTemplate {

  @Override
  public EmailTemplateType type() {
    return EmailTemplateType.EMAIL_VERIFICATION_OTP;
  }

  @Override
  public String subject() {
    return "Verify your email";
  }

  @Override
  public String title() {
    return "Verify your email address";
  }

  @Override
  public String message() {
    return "Use the verification code below to finish setting up your account.";
  }

  @Override
  public String securityNote() {
    return "If you did not create this account, you can safely ignore this email.";
  }
}

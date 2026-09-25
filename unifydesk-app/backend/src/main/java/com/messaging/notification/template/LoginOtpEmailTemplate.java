package com.messaging.notification.template;

import org.springframework.stereotype.Component;

@Component
public class LoginOtpEmailTemplate implements OtpEmailTemplate {

  @Override
  public EmailTemplateType type() {
    return EmailTemplateType.LOGIN_OTP;
  }

  @Override
  public String subject() {
    return "Your login verification code";
  }

  @Override
  public String title() {
    return "Confirm your sign in";
  }

  @Override
  public String message() {
    return "Use this one-time code to complete your sign in.";
  }

  @Override
  public String securityNote() {
    return "If you did not try to sign in, do not share this code with anyone.";
  }
}

package com.messaging.notification.template;

public interface OtpEmailTemplate {

  EmailTemplateType type();

  String subject();

  String title();

  String message();

  String securityNote();
}

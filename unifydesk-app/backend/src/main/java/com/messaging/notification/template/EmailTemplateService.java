package com.messaging.notification.template;

import com.messaging.common.exception.BadRequestException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class EmailTemplateService {

  private final EmailBaseLayout layout;
  private final Map<EmailTemplateType, OtpEmailTemplate> otpTemplates;

  public EmailTemplateService(EmailBaseLayout layout, List<OtpEmailTemplate> templates) {
    this.layout = layout;
    EnumMap<EmailTemplateType, OtpEmailTemplate> byType = new EnumMap<>(EmailTemplateType.class);
    for (OtpEmailTemplate template : templates) {
      if (byType.put(template.type(), template) != null) {
        throw new IllegalStateException("Duplicate email template: " + template.type());
      }
    }
    this.otpTemplates = Map.copyOf(byType);
  }

  public EmailTemplateContent renderOtp(EmailTemplateType type, String code, long expiryMinutes) {
    OtpEmailTemplate template = otpTemplates.get(type);
    if (template == null) {
      throw new BadRequestException("Unsupported OTP email template");
    }
    if (code == null || code.isBlank()) {
      throw new BadRequestException("OTP code is required");
    }

    long minutes = Math.max(1, expiryMinutes);
    String normalizedCode = code.trim();
    String expiryText = minutes == 1 ? "1 minute" : minutes + " minutes";

    String textBody =
        """
        %s

        %s

        Verification code: %s

        This code expires in %s.

        %s
        """
            .formatted(
                template.title(),
                template.message(),
                normalizedCode,
                expiryText,
                template.securityNote())
            .trim();

    String safeCode = HtmlUtils.htmlEscape(normalizedCode);
    String content =
        """
        <p style="margin:0 0 22px;font-size:15px;line-height:24px;color:#475569;">%s</p>
        <div style="margin:0 0 22px;padding:18px 20px;border:1px solid #e0e7ff;border-radius:12px;background:#f8faff;text-align:center;">
          <p style="margin:0 0 8px;font-size:11px;line-height:16px;font-weight:700;letter-spacing:0.12em;text-transform:uppercase;color:#64748b;">Verification code</p>
          <div style="font-family:'SFMono-Regular',Consolas,'Liberation Mono',monospace;font-size:32px;line-height:40px;font-weight:800;letter-spacing:0.18em;color:%s;">%s</div>
        </div>
        <p style="margin:0 0 18px;font-size:13px;line-height:21px;color:#64748b;">This code expires in <strong style="color:#334155;">%s</strong>.</p>
        <div style="padding:13px 15px;border-radius:10px;background:#f8fafc;">
          <p style="margin:0;font-size:12px;line-height:19px;color:#64748b;">%s</p>
        </div>
        """
            .formatted(
                HtmlUtils.htmlEscape(template.message()),
                layout.primaryColor(),
                safeCode,
                HtmlUtils.htmlEscape(expiryText),
                HtmlUtils.htmlEscape(template.securityNote()));

    String htmlBody = layout.render(template.message(), template.title(), content);

    return new EmailTemplateContent(template.subject(), textBody, htmlBody);
  }
}

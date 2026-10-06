package com.messaging.notification.template;

import com.messaging.notification.config.NotificationProperties;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

@Component
@RequiredArgsConstructor
public class EmailBaseLayout {

  private final NotificationProperties properties;

  public String render(String preheader, String title, String contentHtml) {
    NotificationProperties.Email email = properties.getEmail();
    String brandName = escape(defaultText(email.getBrandName(), "Messaging"));
    String websiteUrl = escapeAttribute(email.getWebsiteUrl());
    String logoUrl = escapeAttribute(email.getLogoUrl());
    String supportEmail = escape(defaultText(email.getSupportEmail(), ""));
    String supportUrl = escapeAttribute(email.getSupportUrl());
    String primaryColor = safeColor(email.getPrimaryColor());
    String safePreheader = escape(defaultText(preheader, ""));
    String safeTitle = escape(defaultText(title, ""));

    String brand =
        StringUtils.hasText(logoUrl)
            ? """
              <img src="%s" alt="%s" style="display:block;max-height:36px;max-width:180px;border:0;" />
              """
                .formatted(logoUrl, brandName)
            : """
              <span style="font-size:21px;line-height:28px;font-weight:800;color:#0f172a;">%s</span>
              """
                .formatted(brandName);

    String brandLink =
        StringUtils.hasText(websiteUrl)
            ? """
              <a href="%s" style="text-decoration:none;">%s</a>
              """
                .formatted(websiteUrl, brand)
            : brand;

    String support =
        StringUtils.hasText(supportUrl)
            ? """
              <a href="%s" style="color:#64748b;text-decoration:underline;">Get help</a>
              """
                .formatted(supportUrl)
            : StringUtils.hasText(supportEmail)
                ? """
                  <a href="mailto:%s" style="color:#64748b;text-decoration:underline;">%s</a>
                  """
                    .formatted(escapeAttribute(email.getSupportEmail()), supportEmail)
                : "";

    String supportLine =
        StringUtils.hasText(support)
            ? "Need help? " + support
            : "This is an automated security email.";

    return """
      <!doctype html>
      <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <meta name="color-scheme" content="light" />
          <meta name="supported-color-schemes" content="light" />
          <title>%s</title>
        </head>
        <body style="margin:0;padding:0;background:#f8fafc;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;color:#0f172a;">
          <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">%s</div>
          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;background:#f8fafc;">
            <tr>
              <td align="center" style="padding:32px 16px;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;max-width:600px;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;overflow:hidden;">
                  <tr>
                    <td style="padding:24px 28px;border-bottom:1px solid #eef2f7;">
                      %s
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:32px 28px;">
                      <h1 style="margin:0 0 16px;font-size:24px;line-height:32px;font-weight:800;color:#0f172a;">%s</h1>
                      %s
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:20px 28px;border-top:1px solid #eef2f7;background:#fbfdff;">
                      <p style="margin:0 0 6px;font-size:12px;line-height:18px;color:#64748b;">%s</p>
                      <p style="margin:0;font-size:11px;line-height:17px;color:#94a3b8;">Copyright %s %s. All rights reserved.</p>
                    </td>
                  </tr>
                </table>
                <div style="height:6px;width:48px;margin:18px auto 0;border-radius:999px;background:%s;"></div>
              </td>
            </tr>
          </table>
        </body>
      </html>
      """
        .formatted(
            safeTitle,
            safePreheader,
            brandLink,
            safeTitle,
            contentHtml,
            supportLine,
            Year.now().getValue(),
            brandName,
            primaryColor);
  }

  public String primaryColor() {
    return safeColor(properties.getEmail().getPrimaryColor());
  }

  private String safeColor(String value) {
    if (value != null && value.matches("^#[0-9A-Fa-f]{6}$")) {
      return value;
    }
    return "#4f46e5";
  }

  private String escape(String value) {
    return HtmlUtils.htmlEscape(value == null ? "" : value);
  }

  private String escapeAttribute(String value) {
    return HtmlUtils.htmlEscape(value == null ? "" : value);
  }

  private String defaultText(String value, String fallback) {
    return StringUtils.hasText(value) ? value.trim() : fallback;
  }
}

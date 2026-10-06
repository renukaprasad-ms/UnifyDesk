package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.transport.BroadcastEmailComplianceMetadata;
import com.messaging.contacts.consent.RecipientActionProperties;
import com.messaging.contacts.consent.RecipientActionTokenService;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class BroadcastEmailComplianceService {

  public static final String MSG91_UNSUBSCRIBE_VARIABLE = "unsubscribe_url";
  private static final String HTML_MARKER = "data-unifydesk-unsubscribe=\"1\"";

  private final RecipientActionTokenService tokenService;
  private final RecipientActionProperties properties;

  public CompliantContent apply(
      BroadcastTemplateRenderer.RenderedContent rendered, BroadcastRecipient recipient) {
    if (recipient.getContactId() == null || recipient.getContactIdentityId() == null) {
      throw new IllegalStateException("Canonical Email Broadcast recipient is missing Contact identity");
    }

    String token =
        tokenService.issue(
            recipient.getCompanyId(), recipient.getContactId(), recipient.getContactIdentityId());
    String visibleUrl =
        baseUrl(properties.getFrontendBaseUrl(), "frontend")
            + "/unsubscribe?token="
            + token;
    String oneClickUrl =
        baseUrl(properties.getPublicApiBaseUrl(), "public API")
            + "/api/v1/public/recipient-actions/email/unsubscribe/"
            + token;

    String text = appendText(rendered.bodyText(), visibleUrl);
    String html = appendHtml(rendered.bodyHtml(), rendered.bodyText(), visibleUrl);
    Map<String, String> variables = new LinkedHashMap<>(rendered.templateVariables());
    variables.put(MSG91_UNSUBSCRIBE_VARIABLE, visibleUrl);

    return new CompliantContent(
        rendered.subject(),
        text,
        html,
        Map.copyOf(variables),
        new BroadcastEmailComplianceMetadata(visibleUrl, oneClickUrl));
  }

  private String appendText(String bodyText, String visibleUrl) {
    String body = bodyText == null ? "" : bodyText;
    return body + "\n\nUnsubscribe from marketing emails: " + visibleUrl;
  }

  private String appendHtml(String bodyHtml, String bodyText, String visibleUrl) {
    String body = bodyHtml;
    if (body == null || body.isBlank()) {
      body =
          "<div>"
              + HtmlUtils.htmlEscape(bodyText == null ? "" : bodyText).replace("\n", "<br>")
              + "</div>";
    }
    if (body.contains(HTML_MARKER)) return body;
    return body
        + "<div "
        + HTML_MARKER
        + " style=\"margin-top:24px;font-size:12px\"><a href=\""
        + HtmlUtils.htmlEscape(visibleUrl)
        + "\">Unsubscribe from marketing emails</a></div>";
  }

  private String baseUrl(String configured, String name) {
    if (configured == null || configured.isBlank()) {
      throw new IllegalStateException("Recipient action " + name + " base URL is required");
    }
    String value = configured.trim();
    URI uri;
    try {
      uri = URI.create(value);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Recipient action " + name + " base URL is invalid", exception);
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getQuery() != null
        || uri.getFragment() != null) {
      throw new IllegalStateException(
          "Recipient action " + name + " base URL must be an HTTPS origin");
    }
    while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
    return value;
  }

  public record CompliantContent(
      String subject,
      String bodyText,
      String bodyHtml,
      Map<String, String> templateVariables,
      BroadcastEmailComplianceMetadata compliance) {}
}

package com.messaging.notification.platform.delivery.email;

import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.model.DeliveryModels.RenderedEmail;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
@RequiredArgsConstructor
public class NotificationEmailRenderer {

  private final NotificationDeliveryProperties properties;

  public RenderedEmail companyWelcome(String userName, String workspaceName) {
    String safeUser = plain(userName, 120);
    String safeWorkspace = plain(workspaceName, 160);
    String escapedUser = HtmlUtils.htmlEscape(safeUser);
    String escapedWorkspace = HtmlUtils.htmlEscape(safeWorkspace);
    String workspaceUrl = trustedUrl("/workspace");
    String subject = subject("Welcome to UnifyDesk - " + safeWorkspace + " is ready");
    String text =
        "Hi "
            + safeUser
            + ",\n\nYour workspace "
            + safeWorkspace
            + " is ready.\nOpen UnifyDesk: "
            + workspaceUrl
            + "\n\n- UnifyDesk";
    String html =
        "<p>Hi "
            + escapedUser
            + ",</p><p>Your workspace <strong>"
            + escapedWorkspace
            + "</strong> is ready.</p><p><a href=\""
            + HtmlUtils.htmlEscape(workspaceUrl)
            + "\">Open UnifyDesk</a></p><p>- UnifyDesk</p>";
    return new RenderedEmail(subject, text, html);
  }

  public RenderedEmail inboxDigest(String workspaceName, long unreadCount) {
    String safeWorkspace = plain(workspaceName, 160);
    String escapedWorkspace = HtmlUtils.htmlEscape(safeWorkspace);
    String inboxUrl = trustedUrl("/inbox/email");
    String preferencesUrl = trustedUrl("/settings/account?section=notifications");
    String subject = subject("You have unread activity in " + safeWorkspace);
    String countText = Long.toString(Math.max(0, unreadCount));
    String text =
        "You have "
            + countText
            + " unread inbox items in "
            + safeWorkspace
            + ".\n\nOpen inbox: "
            + inboxUrl
            + "\nManage notification preferences: "
            + preferencesUrl
            + "\n\nThis digest intentionally does not include message content.";
    String html =
        "<p>You have <strong>"
            + countText
            + "</strong> unread inbox items in <strong>"
            + escapedWorkspace
            + "</strong>.</p><p><a href=\""
            + HtmlUtils.htmlEscape(inboxUrl)
            + "\">Open inbox</a></p><p><a href=\""
            + HtmlUtils.htmlEscape(preferencesUrl)
            + "\">Manage notification preferences</a></p>"
            + "<p>This digest intentionally does not include message content.</p>";
    return new RenderedEmail(subject, text, html);
  }

  private String trustedUrl(String path) {
    String base = properties.getFrontendBaseUrl();
    try {
      URI uri = new URI(base);
      if (uri.getScheme() == null
          || (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null) {
        throw new IllegalStateException("Notification frontend base URL is invalid");
      }
      String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
      return normalized + path;
    } catch (URISyntaxException exception) {
      throw new IllegalStateException("Notification frontend base URL is invalid", exception);
    }
  }

  private String subject(String value) {
    return plain(value, 200);
  }

  private String plain(String value, int max) {
    String normalized = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", " ").trim();
    if (normalized.length() > max) {
      return normalized.substring(0, max);
    }
    return normalized;
  }
}

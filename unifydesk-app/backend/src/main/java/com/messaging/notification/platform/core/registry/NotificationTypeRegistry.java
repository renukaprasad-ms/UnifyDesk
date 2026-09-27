package com.messaging.notification.platform.core.registry;

import com.messaging.common.exception.BadRequestException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class NotificationTypeRegistry {

  public enum EmailMode {
    OFF,
    IMMEDIATE,
    DELAYED,
    DIGEST
  }

  public record Definition(
      String sourceEventType,
      String notificationType,
      String label,
      String category,
      String severity,
      Set<String> allowedVariables,
      boolean coalesced,
      boolean mandatoryEmail,
      List<EmailMode> allowedEmailModes,
      EmailMode defaultEmailMode,
      boolean defaultBrowserEnabled) {}

  private final Map<String, Definition> byNotification = definitionsByNotification();
  private final Map<String, Definition> bySource =
      byNotification.values().stream()
          .filter(definition -> definition.sourceEventType() != null)
          .collect(
              java.util.stream.Collectors.toUnmodifiableMap(
                  Definition::sourceEventType, definition -> definition));

  public Definition requireSource(String eventType, int schemaVersion) {
    if (schemaVersion != 1) {
      throw new BadRequestException("Unsupported notification event schema");
    }
    Definition definition = bySource.get(eventType);
    if (definition == null) {
      throw new BadRequestException("Unsupported notification event type");
    }
    return definition;
  }

  public List<Definition> definitions() {
    return byNotification.values().stream()
        .sorted(Comparator.comparing(Definition::category).thenComparing(Definition::notificationType))
        .toList();
  }

  public Definition requireNotificationType(String notificationType) {
    Definition definition = byNotification.get(notificationType);
    if (definition == null) {
      throw new BadRequestException("Unsupported notification type");
    }
    return definition;
  }

  public void validateVariables(Definition definition, Map<String, String> variables) {
    if (!definition.allowedVariables().equals(variables.keySet())) {
      throw new BadRequestException("Invalid notification variables");
    }
    variables.values().forEach(
        value -> {
          if (value == null
              || value.length() > 512
              || value.chars().anyMatch(Character::isISOControl)) {
            throw new BadRequestException("Invalid notification variable");
          }
        });
  }

  public Rendered render(Definition definition, Map<String, String> variables) {
    return switch (definition.notificationType()) {
      case "COMPANY_CREATED" ->
          new Rendered(
              "Welcome to UnifyDesk",
              "Your workspace " + variables.get("companyName") + " is ready.",
              "/workspace");
      case "INBOX_ACTIVITY" ->
          new Rendered(
              "New inbox activity",
              "You have new inbox messages.",
              "/inbox/email?connectionId=" + variables.get("connectionId"));
      case "COMPANY_INVITATION" ->
          new Rendered(
              "Workspace invitation",
              "You have been invited to join " + variables.get("companyName") + ".",
              "/account/invitations");
      case "COMPANY_INVITATION_ACCEPTED" ->
          new Rendered(
              "Invitation accepted",
              variables.get("memberName") + " joined " + variables.get("companyName") + ".",
              "/company/users");
      default -> throw new BadRequestException("Notification type has no source renderer");
    };
  }

  private Map<String, Definition> definitionsByNotification() {
    Map<String, Definition> values = new LinkedHashMap<>();
    add(
        values,
        new Definition(
            null,
            "SECURITY_ACCOUNT_CRITICAL",
            "Security alerts",
            "SECURITY",
            "CRITICAL",
            Set.of(),
            false,
            true,
            List.of(EmailMode.IMMEDIATE),
            EmailMode.IMMEDIATE,
            false));
    add(
        values,
        type(
            "BILLING_PAYMENT_FAILED",
            "Payment failed",
            "BILLING",
            "CRITICAL",
            List.of(EmailMode.OFF, EmailMode.IMMEDIATE),
            EmailMode.IMMEDIATE,
            true));
    add(
        values,
        type(
            "BILLING_SUBSCRIPTION_CHANGED",
            "Subscription changes",
            "BILLING",
            "NORMAL",
            List.of(EmailMode.OFF, EmailMode.IMMEDIATE),
            EmailMode.IMMEDIATE,
            false));
    add(
        values,
        new Definition(
            "company.created",
            "COMPANY_CREATED",
            "Workspace created",
            "COMPANY",
            "NORMAL",
            Set.of("companyName"),
            false,
            true,
            List.of(EmailMode.IMMEDIATE),
            EmailMode.IMMEDIATE,
            false));
    add(
        values,
        new Definition(
            "company.invitation.created",
            "COMPANY_INVITATION",
            "Workspace invitations",
            "COMPANY",
            "NORMAL",
            Set.of("companyName"),
            false,
            true,
            List.of(EmailMode.IMMEDIATE),
            EmailMode.IMMEDIATE,
            true));
    add(
        values,
        new Definition(
            "company.invitation.accepted",
            "COMPANY_INVITATION_ACCEPTED",
            "Invitation accepted",
            "COMPANY",
            "NORMAL",
            Set.of("companyName", "memberName"),
            false,
            false,
            List.of(EmailMode.OFF),
            EmailMode.OFF,
            true));
    add(
        values,
        type(
            "MEMBER_OR_ROLE_CHANGED",
            "Member and role changes",
            "COMPANY",
            "NORMAL",
            List.of(EmailMode.OFF, EmailMode.DELAYED),
            EmailMode.OFF,
            false));
    add(
        values,
        type(
            "APPROVAL_REQUIRED",
            "Approvals required",
            "APPROVALS",
            "HIGH",
            List.of(EmailMode.OFF, EmailMode.DELAYED),
            EmailMode.DELAYED,
            true));
    add(
        values,
        type(
            "APPROVAL_DECIDED",
            "Approval decisions",
            "APPROVALS",
            "NORMAL",
            List.of(EmailMode.OFF, EmailMode.DELAYED),
            EmailMode.OFF,
            false));
    add(
        values,
        type(
            "BROADCAST_COMPLETED",
            "Broadcast completed",
            "BROADCAST",
            "NORMAL",
            List.of(EmailMode.OFF, EmailMode.DELAYED),
            EmailMode.OFF,
            false));
    add(
        values,
        type(
            "BROADCAST_FAILED",
            "Broadcast failed",
            "BROADCAST",
            "HIGH",
            List.of(EmailMode.OFF, EmailMode.DELAYED),
            EmailMode.DELAYED,
            true));
    add(
        values,
        new Definition(
            "inbox.activity",
            "INBOX_ACTIVITY",
            "Inbox activity",
            "INBOX",
            "NORMAL",
            Set.of("connectionId"),
            true,
            false,
            List.of(EmailMode.OFF, EmailMode.DIGEST),
            EmailMode.OFF,
            false));
    add(
        values,
        type(
            "INTEGRATION_DEGRADED",
            "Integration degraded",
            "INTEGRATION",
            "HIGH",
            List.of(EmailMode.OFF, EmailMode.DELAYED),
            EmailMode.DELAYED,
            true));
    add(
        values,
        type(
            "INTEGRATION_DISCONNECTED",
            "Integration disconnected",
            "INTEGRATION",
            "HIGH",
            List.of(EmailMode.OFF, EmailMode.DELAYED),
            EmailMode.DELAYED,
            true));
    add(
        values,
        type(
            "BACKGROUND_JOB_COMPLETED",
            "Background job completed",
            "JOBS",
            "NORMAL",
            List.of(EmailMode.OFF, EmailMode.DELAYED),
            EmailMode.OFF,
            false));
    add(
        values,
        type(
            "BACKGROUND_JOB_FAILED",
            "Background job failed",
            "JOBS",
            "HIGH",
            List.of(EmailMode.OFF, EmailMode.DELAYED),
            EmailMode.DELAYED,
            true));
    add(
        values,
        type(
            "QUOTA_THRESHOLD",
            "Usage thresholds",
            "USAGE",
            "HIGH",
            List.of(EmailMode.OFF, EmailMode.DELAYED),
            EmailMode.OFF,
            false));
    add(
        values,
        type(
            "ONBOARDING_GUIDANCE",
            "Product guidance",
            "ONBOARDING",
            "LOW",
            List.of(EmailMode.OFF),
            EmailMode.OFF,
            false));
    add(
        values,
        type(
            "LOW_PRIORITY_INFO",
            "Product information",
            "INFO",
            "LOW",
            List.of(EmailMode.OFF),
            EmailMode.OFF,
            false));
    return Map.copyOf(values);
  }

  private Definition type(
      String notificationType,
      String label,
      String category,
      String severity,
      List<EmailMode> modes,
      EmailMode defaultMode,
      boolean browser) {
    return new Definition(
        null,
        notificationType,
        label,
        category,
        severity,
        Set.of(),
        false,
        false,
        modes,
        defaultMode,
        browser);
  }

  private void add(Map<String, Definition> values, Definition definition) {
    values.put(definition.notificationType(), definition);
  }

  public record Rendered(String title, String body, String actionPath) {
    public Rendered {
      if (actionPath == null
          || !actionPath.startsWith("/")
          || actionPath.startsWith("//")
          || actionPath.contains("\\")
          || actionPath.chars().anyMatch(Character::isISOControl)) {
        throw new BadRequestException("Invalid notification action");
      }
    }
  }
}

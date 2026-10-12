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
    boolean legacyInboxActivity =
        "INBOX_ACTIVITY".equals(definition.notificationType())
            && variables.keySet().equals(Set.of("connectionId"));
    if (!definition.allowedVariables().equals(variables.keySet()) && !legacyInboxActivity) {
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
      case "CRM_TASK_DUE" ->
          new Rendered(
              "CRM follow-up due",
              "Follow-up: " + variables.get("taskTitle"),
              "/crm/tasks");
      case "COMPANY_CREATED" ->
          new Rendered(
              "Welcome to UnifyDesk",
              "Your workspace " + variables.get("companyName") + " is ready.",
              "/workspace");
      case "INBOX_ACTIVITY" ->
          new Rendered(
              variables.containsKey("sender")
                  ? "New email from " + variables.get("sender")
                  : "New inbox activity",
              inboxBody(variables),
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
      case "BROADCAST_COMPLETED" ->
          new Rendered(
              "Broadcast completed",
              "Your broadcast finished processing.",
              broadcastPath(variables.get("channel")));
      case "BROADCAST_FAILED" ->
          new Rendered(
              "Broadcast needs attention",
              "Your broadcast finished with delivery failures.",
              broadcastPath(variables.get("channel")));
      default -> throw new BadRequestException("Notification type has no source renderer");
    };
  }

  private String broadcastPath(String channel) {
    if ("EMAIL".equalsIgnoreCase(channel)) return "/broadcast/email";
    if ("SMS".equalsIgnoreCase(channel)) return "/broadcast/sms";
    if ("WHATSAPP".equalsIgnoreCase(channel)) return "/broadcast/social-media";
    return "/workspace";
  }

  private String inboxBody(Map<String, String> variables) {
    if (!variables.containsKey("subject")) {
      return "You have a new inbox message.";
    }
    return String.join(
        "\n",
        variables.get("subject"),
        variables.getOrDefault("snippet", ""),
        variables.getOrDefault("inboxName", "Inbox"));
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
            "crm.task.due",
            "CRM_TASK_DUE",
            "CRM task reminders",
            "CRM",
            "NORMAL",
            Set.of("taskId", "taskTitle", "generation"),
            false,
            false,
            List.of(EmailMode.OFF),
            EmailMode.OFF,
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
        new Definition(
            "broadcast.completed",
            "BROADCAST_COMPLETED",
            "Broadcast completed",
            "BROADCAST",
            "NORMAL",
            Set.of("runId", "broadcastId", "channel"),
            false,
            false,
            List.of(EmailMode.OFF, EmailMode.DELAYED),
            EmailMode.OFF,
            false));
    add(
        values,
        new Definition(
            "broadcast.failed",
            "BROADCAST_FAILED",
            "Broadcast failed",
            "BROADCAST",
            "HIGH",
            Set.of("runId", "broadcastId", "channel"),
            false,
            false,
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
            Set.of("connectionId", "sender", "subject", "snippet", "inboxName"),
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

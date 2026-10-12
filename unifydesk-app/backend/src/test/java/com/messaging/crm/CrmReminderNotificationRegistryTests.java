package com.messaging.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.notification.platform.core.registry.NotificationTypeRegistry;
import com.messaging.notification.platform.core.registry.NotificationTypeRegistry.EmailMode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrmReminderNotificationRegistryTests {

  @Test
  void crmReminderUsesInAppByDefaultAndOnlyTaskSafeVariables() {
    NotificationTypeRegistry registry = new NotificationTypeRegistry();
    var definition = registry.requireSource("crm.task.due", 1);
    assertThat(definition.notificationType()).isEqualTo("CRM_TASK_DUE");
    assertThat(definition.defaultEmailMode()).isEqualTo(EmailMode.OFF);
    assertThat(definition.allowedVariables()).containsExactlyInAnyOrder("taskId", "taskTitle", "generation");

    Map<String, String> variables =
        Map.of("taskId", "401", "taskTitle", "Contact the prospect", "generation", "0");
    registry.validateVariables(definition, variables);
    var rendered = registry.render(definition, variables);
    assertThat(rendered.title()).contains("follow-up");
    assertThat(rendered.body()).contains("Contact the prospect");
    assertThat(rendered.actionPath()).isEqualTo("/crm/tasks");
  }
}

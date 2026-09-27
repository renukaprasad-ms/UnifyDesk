package com.messaging.managedsms.control;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.dto.ManagedSmsDtos;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.msg91.Msg91ManagedSmsProviderAdapter;
import com.messaging.managedsms.control.provisioning.ManagedSmsProviderAdapter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ManagedSmsControlContractTests {

  @Test
  void msg91AdapterNeverInventsProviderSuccessWhenProvisioningContractIsUnverified() {
    ManagedSmsProperties properties = new ManagedSmsProperties();
    properties.setEnabled(true);
    properties.setMasterAuthKey("platform-secret");
    Msg91ManagedSmsProviderAdapter adapter = new Msg91ManagedSmsProviderAdapter(properties);

    var result =
        adapter.execute(
            new ManagedSmsProviderAdapter.ProviderRequest(
                ProvisioningOperationType.ENSURE_TEMPLATE_BINDING,
                1L,
                2L,
                3L,
                null,
                "PE123",
                "UNIFY",
                "DLT123",
                "Hi {#var#}"));

    assertThat(result.outcome()).isEqualTo(ManagedSmsProviderAdapter.Outcome.ACTION_REQUIRED);
    assertThat(result.providerClientRef()).isNull();
    assertThat(result.providerTemplateRef()).isNull();
    assertThat(result.providerFlowRef()).isNull();
  }

  @Test
  void customerDtosDoNotExposePlatformProviderSecretsOrProviderReferences() {
    var names =
        Arrays.stream(ManagedSmsDtos.class.getDeclaredClasses())
            .filter(Class::isRecord)
            .flatMap(type -> Arrays.stream(type.getRecordComponents()))
            .map(component -> component.getName().toLowerCase())
            .toList();

    assertThat(names).noneMatch(name -> name.contains("authkey"));
    assertThat(names).noneMatch(name -> name.contains("masterauth"));
    assertThat(names).noneMatch(name -> name.contains("providerclientref"));
    assertThat(names).noneMatch(name -> name.contains("provideraccountref"));
    assertThat(names).noneMatch(name -> name.contains("providertemplateref"));
  }

  @Test
  void migrationEnforcesOneManagedConnectionAndActivatedTemplateImmutability() throws Exception {
    try (var stream =
        getClass()
            .getResourceAsStream("/db/migration/V62__managed_sms_control_plane.sql")) {
      assertThat(stream).isNotNull();
      String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(sql).contains("uk_broadcast_sender_connections_managed_msg91_sms_company");
      assertThat(sql).contains("management_mode = 'MESSAGING_MANAGED'");
      assertThat(sql).contains("managed_sms_protect_activated_template");
      assertThat(sql).contains("Activated managed SMS template content is immutable");
    }
  }

  @Test
  void roundTwoMigrationCreatesCustomerComplianceAuditLedger() throws Exception {
    try (var stream =
        getClass()
            .getResourceAsStream(
                "/db/migration/V65__managed_sms_round2_control_security.sql")) {
      assertThat(stream).isNotNull();
      String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(sql).contains("create table managed_sms_compliance_actions");
      assertThat(sql).contains("actor_user_id bigint not null");
      assertThat(sql).contains("before_state_json text not null");
      assertThat(sql).contains("after_state_json text not null");
    }
  }
}

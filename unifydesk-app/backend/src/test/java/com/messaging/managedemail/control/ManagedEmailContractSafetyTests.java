package com.messaging.managedemail.control;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.managedemail.control.dto.ManagedEmailDtos.SenderResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ManagedEmailContractSafetyTests {

  @Test
  void customerFacingSenderDtoContainsNoProviderSecretsOrInternalTenantNames() {
    var names =
        Arrays.stream(SenderResponse.class.getRecordComponents())
            .map(component -> component.getName().toLowerCase(Locale.ROOT))
            .toList();

    assertThat(names)
        .noneMatch(
            name ->
                name.contains("credential")
                    || name.contains("rolearn")
                    || name.contains("awsaccount")
                    || name.contains("tenantname")
                    || name.contains("configurationset"));
  }

  @Test
  void hostedDomainReservationIndexHasNoTerminalStatusEscapeHatch() throws Exception {
    try (var stream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("db/migration/V56__managed_email_control_plane.sql")) {
      assertThat(stream).isNotNull();
      String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      int start = sql.indexOf("create unique index uk_managed_email_hosted_domain_permanent");
      int end = sql.indexOf("create index idx_managed_email_domains_company_status", start);
      String hostedReservationIndex = sql.substring(start, end).toLowerCase(Locale.ROOT);

      assertThat(hostedReservationIndex).contains("domain_type = 'unifydesk_hosted'");
      assertThat(hostedReservationIndex).doesNotContain("status not in");
      assertThat(hostedReservationIndex).doesNotContain("deleted");
      assertThat(hostedReservationIndex).doesNotContain("retired");
    }
  }
}

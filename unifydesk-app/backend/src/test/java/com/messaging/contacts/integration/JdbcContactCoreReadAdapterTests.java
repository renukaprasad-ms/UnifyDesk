package com.messaging.contacts.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.contacts.consent.ConsentTypes.Channel;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcContactCoreReadAdapterTests {

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = 23L)
  void identityLookupUsesTheInnerAliasForAnExplicitIdentity(Long identityId) {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    JdbcContactCoreReadAdapter adapter = new JdbcContactCoreReadAdapter(jdbc);

    assertThat(adapter.loadIdentity(7L, 11L, identityId, Channel.EMAIL)).isEmpty();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
    verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
    String subquery = sql.getValue().split("join lateral \\(")[1].split("\\) i on true")[0];
    assertThat(subquery).doesNotContain("and i.id");
    if (identityId == null) {
      assertThat(subquery).doesNotContain(":identityId");
    } else {
      assertThat(subquery).contains("and ci.id = :identityId");
    }
    assertThat(params.getValue())
        .containsEntry("companyId", 7L)
        .containsEntry("contactId", 11L)
        .containsEntry("identityId", identityId)
        .containsEntry("identityType", "EMAIL");
  }
}

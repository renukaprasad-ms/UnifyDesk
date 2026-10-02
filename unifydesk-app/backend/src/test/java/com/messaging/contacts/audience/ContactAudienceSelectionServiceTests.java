package com.messaging.contacts.audience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.messaging.common.exception.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ContactAudienceSelectionServiceTests {

  @Test
  void boundedSelectionDeduplicatesWithoutLosingOriginalAccounting() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);
    ContactAudienceSelectionService service = new ContactAudienceSelectionService(jdbc, named);

    when(named.queryForList(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), eq(Long.class)))
        .thenReturn(List.of(10L, 11L));

    ContactAudienceSelectionService.PreviewSelection result =
        service.validate(7L, List.of(10L, 10L, 11L));

    assertThat(result.contactIds()).containsExactly(10L, 11L);
    assertThat(result.duplicateCount()).isEqualTo(1);
    assertThat(result.selectionHash()).hasSize(64);
  }

  @Test
  void foreignOrMissingContactFailsClosed() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);
    ContactAudienceSelectionService service = new ContactAudienceSelectionService(jdbc, named);

    when(named.queryForList(anyString(), any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class), eq(Long.class)))
        .thenReturn(List.of(10L));

    assertThatThrownBy(() -> service.validate(7L, List.of(10L, 99L)))
        .isInstanceOf(NotFoundException.class);
  }
}

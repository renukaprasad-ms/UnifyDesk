package com.messaging.contacts.audience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AudienceSourceResolverTests {
  @Test
  void segmentCaptureSeparatesWhereKeywordFromCompiledPredicate() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    NamedParameterJdbcTemplate namedJdbc = mock(NamedParameterJdbcTemplate.class);
    SegmentRepository repository = mock(SegmentRepository.class);
    SegmentCompiler compiler = mock(SegmentCompiler.class);
    ObjectMapper mapper = mock(ObjectMapper.class);
    SegmentRepository.SegmentRow segment = mock(SegmentRepository.SegmentRow.class);
    when(segment.version()).thenReturn(3L);
    when(segment.filterHash()).thenReturn("hash");
    when(segment.filterJson()).thenReturn("{}");
    when(repository.require(7L, 11L)).thenReturn(segment);
    when(jdbc.query(anyString(), any(RowMapper.class), eq(7L), eq(11L)))
        .thenReturn(List.of(11L));
    SegmentFilter filter = mock(SegmentFilter.class);
    when(mapper.readValue("{}", SegmentFilter.class)).thenReturn(filter);
    when(compiler.compile(7L, filter))
        .thenReturn(new SegmentCompiler.CompiledSegment(
            "c.company_id = :companyId AND c.status = 'ACTIVE'",
            Map.of("companyId", 7L), null, "hash", 1));
    AudienceSourceResolver resolver =
        new AudienceSourceResolver(jdbc, namedJdbc, repository, compiler, mapper);

    resolver.captureCandidates(7L, new AudienceSourceResolver.SourceDescriptor(
        AudienceSourceResolver.SourceType.SEGMENT, 11L, "3", "hash"), 101L);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(namedJdbc).update(sql.capture(), any(MapSqlParameterSource.class));
    assertThat(sql.getValue().replaceAll("\\s+", " "))
        .contains("from contacts c where c.company_id = :companyId")
        .endsWith(" on conflict do nothing");
  }
}

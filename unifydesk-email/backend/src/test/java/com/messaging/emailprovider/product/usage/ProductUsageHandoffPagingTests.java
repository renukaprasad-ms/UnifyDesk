package com.messaging.emailprovider.product.usage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import com.messaging.emailprovider.mail.repository.EmailMessageRepository;
import com.messaging.emailprovider.product.repo.EmailProjectRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ProductUsageHandoffPagingTests {
  private JdbcTemplate jdbc;
  private EmailProjectRepository projects;
  private ProductUsageService service;

  @BeforeEach
  void setUp() {
    jdbc = mock(JdbcTemplate.class);
    projects = mock(EmailProjectRepository.class);
    service = new ProductUsageService(jdbc, projects, mock(EmailMessageRepository.class));
    when(projects.existsById(123L)).thenReturn(true);
  }

  @Test
  void remoteProjectIsRequiredAndNeverInferredFromUnifyDeskCompany() {
    assertThrows(NotFoundException.class, () -> service.handoffs(456L, 0L, 5));
    verifyNoInteractions(jdbc);
  }

  @Test
  void rejectsInvalidCursorAndUnboundedPageRequests() {
    assertThrows(BadRequestException.class, () -> service.handoffs(123L, -1L, 10));
    assertThrows(BadRequestException.class, () -> service.handoffs(123L, 0L, 501));
    assertThrows(BadRequestException.class, () -> service.handoffs(123L, 0L, 0));
    verifyNoInteractions(jdbc);
  }

  @Test
  @SuppressWarnings("unchecked")
  void paginationReturnsLimitedRowsAndLastVisibleCursor() {
    when(jdbc.query(anyString(), any(RowMapper.class), eq(123L), eq(10L), eq(3)))
        .thenReturn(List.of(
            new ProductUsageService.HandoffEntry("11", "101", "201",
                Instant.parse("2026-10-09T12:00:00Z"), 12000L),
            new ProductUsageService.HandoffEntry("12", "102", "202",
                Instant.parse("2026-10-09T12:01:00Z"), 12000L),
            new ProductUsageService.HandoffEntry("13", "103", "203",
                Instant.parse("2026-10-09T12:02:00Z"), 12000L)));

    var page = service.handoffs(123L, 10L, 2);
    assertEquals("123", page.projectId());
    assertEquals(2, page.entries().size());
    assertEquals("12", page.nextAfterId());
    assertTrue(page.hasMore());
  }

  @Test
  void dateBoundedReplayRejectsMissingOrOversizedIntervals() {
    var from = Instant.parse("2026-10-01T00:00:00Z");
    assertThrows(BadRequestException.class,
        () -> service.handoffs(123L, 0L, 50, from, null));
    assertThrows(BadRequestException.class,
        () -> service.handoffs(123L, 0L, 50, from, from));
    assertThrows(BadRequestException.class,
        () -> service.handoffs(123L, 0L, 50, from, from.plusSeconds(32L * 86400)));
    verifyNoInteractions(jdbc);
  }

  @Test
  void highWaterMarkIsProjectScoped() {
    when(jdbc.queryForObject(anyString(), eq(Long.class), eq(123L)))
        .thenReturn(9007199254740993L);
    assertEquals("9007199254740993", service.latestUsageId(123L));
  }
}

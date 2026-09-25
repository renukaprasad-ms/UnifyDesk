package com.messaging.company.bootstrap;

import com.messaging.company.service.CompanySystemRoleService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class CompanySystemRoleInitializer implements ApplicationRunner {

  private final JdbcTemplate jdbcTemplate;
  private final CompanySystemRoleService companySystemRoleService;

  @Override
  public void run(ApplicationArguments args) {
    List<Map<String, Object>> companies =
        jdbcTemplate.queryForList(
            """
            select id, created_by_user_id
            from companies
            where status in ('ACTIVE', 'SUSPENDED')
            order by id
            """);

    for (Map<String, Object> company : companies) {
      Long companyId = ((Number) company.get("id")).longValue();
      Long createdByUserId = ((Number) company.get("created_by_user_id")).longValue();
      companySystemRoleService.ensureDefaults(companyId, createdByUserId);
    }

    log.info("company_system_roles_synced companies={}", companies.size());
  }
}

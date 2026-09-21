package com.messaging.company.service;

import com.messaging.company.iam.CompanyPermissionKeys;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemPermissionCatalogService {

  private static final String INSERT_SQL =
      """
      insert into permissions
        (permission_key, resource, action, description, assignable)
      values (?, ?, ?, ?, ?)
      on conflict (permission_key) do nothing
      """;

  private final JdbcTemplate jdbcTemplate;

  public int ensureCatalog() {
    Set<String> existing =
        new HashSet<>(
            jdbcTemplate.queryForList("select permission_key from permissions", String.class));
    int inserted = 0;
    for (String key : permissionKeys()) {
      if (existing.contains(key)) continue;
      inserted +=
          jdbcTemplate.update(
              INSERT_SQL,
              key,
              resource(key),
              action(key),
              description(key),
              assignable(key));
    }
    return inserted;
  }

  private List<String> permissionKeys() {
    return Arrays.stream(CompanyPermissionKeys.class.getFields())
        .filter(field -> field.getType() == String.class)
        .filter(field -> Modifier.isStatic(field.getModifiers()))
        .map(this::stringValue)
        .sorted(Comparator.naturalOrder())
        .toList();
  }

  private String stringValue(Field field) {
    try {
      return (String) field.get(null);
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("Unable to read company permission key", exception);
    }
  }

  private String resource(String key) {
    int separator = key.lastIndexOf('.');
    return separator < 0 ? key : key.substring(0, separator);
  }

  private String action(String key) {
    int separator = key.lastIndexOf('.');
    return separator < 0 ? "access" : key.substring(separator + 1);
  }

  private String description(String key) {
    String resource = resource(key).replace('.', ' ').replace('_', ' ');
    String action = action(key).replace('_', ' ');
    return "Allow " + action + " access to " + resource;
  }

  private boolean assignable(String key) {
    return !CompanyPermissionKeys.COMPANY_DELETE.equals(key)
        && !CompanyPermissionKeys.COMPANY_TRANSFER_OWNERSHIP.equals(key);
  }
}

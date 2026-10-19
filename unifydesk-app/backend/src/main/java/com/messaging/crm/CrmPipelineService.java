package com.messaging.crm;

import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrmPipelineService {

  private final JdbcTemplate jdbc;

  public record StageDto(
      String id,
      String key,
      String name,
      String category,
      int probability,
      int displayOrder,
      String color) {}

  public record PipelineDto(
      String id,
      String name,
      String description,
      boolean isDefault,
      int displayOrder,
      List<StageDto> stages) {}

  public record StageInput(
      String id,
      String key,
      String name,
      String category,
      Integer probability,
      Integer displayOrder,
      String color) {}

  public record CreatePipelineInput(
      String name, String description, Boolean isDefault, List<StageInput> stages) {}

  public record UpdatePipelineInput(
      String name,
      String description,
      Boolean isDefault,
      Integer displayOrder,
      List<StageInput> stages) {}

  @Transactional
  public long ensureDefaultPipeline(long companyId) {
    List<Long> existing =
        jdbc.query(
            "select id from crm_pipelines where company_id = ? order by is_default desc, id asc limit 1",
            (rs, row) -> rs.getLong("id"),
            companyId);
    if (!existing.isEmpty()) {
      return existing.getFirst();
    }

    long pipelineId = IdGenerator.nextId();
    Timestamp now = Timestamp.from(Instant.now());

    jdbc.update(
        """
        insert into crm_pipelines (id, company_id, name, description, is_default, display_order, created_at, updated_at)
        values (?, ?, 'Standard Sales Pipeline', 'Default sales pipeline', true, 0, ?, ?)
        on conflict (company_id, id) do nothing
        """,
        pipelineId,
        companyId,
        now,
        now);

    createStandardStages(companyId, pipelineId, now);
    return pipelineId;
  }

  @Transactional(readOnly = true)
  public List<PipelineDto> list(long companyId) {
    ensureDefaultPipeline(companyId);

    String sql =
        """
        select p.id as p_id, p.name as p_name, p.description as p_desc, p.is_default, p.display_order as p_order,
               s.id as s_id, s.key as s_key, s.name as s_name, s.category as s_cat, s.probability as s_prob,
               s.display_order as s_order, s.color as s_color
        from crm_pipelines p
        left join crm_pipeline_stages s on s.company_id = p.company_id and s.pipeline_id = p.id
        where p.company_id = ?
        order by p.display_order asc, p.id asc, s.display_order asc, s.id asc
        """;

    Map<Long, PipelineBuilder> map = new LinkedHashMap<>();

    jdbc.query(
        sql,
        rs -> {
          long pId = rs.getLong("p_id");
          PipelineBuilder builder =
              map.computeIfAbsent(
                  pId,
                  k -> {
                    try {
                      return new PipelineBuilder(
                          String.valueOf(pId),
                          rs.getString("p_name"),
                          rs.getString("p_desc"),
                          rs.getBoolean("is_default"),
                          rs.getInt("p_order"));
                    } catch (SQLException e) {
                      throw new RuntimeException(e);
                    }
                  });

          try {
            long sId = rs.getLong("s_id");
            if (!rs.wasNull()) {
              builder.stages.add(
                  new StageDto(
                      String.valueOf(sId),
                      rs.getString("s_key"),
                      rs.getString("s_name"),
                      rs.getString("s_cat"),
                      rs.getInt("s_prob"),
                      rs.getInt("s_order"),
                      rs.getString("s_color") != null ? rs.getString("s_color") : "indigo"));
            }
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        },
        companyId);

    return map.values().stream().map(PipelineBuilder::build).toList();
  }

  @Transactional(readOnly = true)
  public PipelineDto get(long companyId, long pipelineId) {
    List<PipelineDto> all = list(companyId);
    return all.stream()
        .filter(p -> p.id().equals(String.valueOf(pipelineId)))
        .findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CRM_PIPELINE_NOT_FOUND"));
  }

  @Transactional
  public PipelineDto create(long companyId, CreatePipelineInput input, long actorUserId) {
    if (input.name() == null || input.name().trim().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "PIPELINE_NAME_REQUIRED");
    }
    String name = input.name().trim();
    if (name.length() > 120) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "PIPELINE_NAME_TOO_LONG");
    }

    boolean isDefault = Boolean.TRUE.equals(input.isDefault());
    if (isDefault) {
      jdbc.update("update crm_pipelines set is_default = false where company_id = ?", companyId);
    }

    long pipelineId = IdGenerator.nextId();
    Timestamp now = Timestamp.from(Instant.now());

    int nextOrder =
        jdbc.queryForObject(
            "select coalesce(max(display_order), 0) + 1 from crm_pipelines where company_id = ?",
            Integer.class,
            companyId);

    jdbc.update(
        """
        insert into crm_pipelines (id, company_id, name, description, is_default, display_order, created_at, updated_at)
        values (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        pipelineId,
        companyId,
        name,
        input.description() != null ? input.description().trim() : null,
        isDefault,
        nextOrder,
        now,
        now);

    if (input.stages() != null && !input.stages().isEmpty()) {
      saveStages(companyId, pipelineId, input.stages(), now);
    } else {
      createStandardStages(companyId, pipelineId, now);
    }

    return get(companyId, pipelineId);
  }

  @Transactional
  public PipelineDto update(
      long companyId, long pipelineId, UpdatePipelineInput input, long actorUserId) {
    PipelineDto existing = get(companyId, pipelineId);

    String name = input.name() != null ? input.name().trim() : existing.name();
    if (name.isBlank() || name.length() > 120) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "PIPELINE_NAME_INVALID");
    }

    Timestamp now = Timestamp.from(Instant.now());

    if (Boolean.TRUE.equals(input.isDefault()) && !existing.isDefault()) {
      jdbc.update("update crm_pipelines set is_default = false where company_id = ?", companyId);
      jdbc.update(
          "update crm_pipelines set is_default = true, updated_at = ? where company_id = ? and id = ?",
          now,
          companyId,
          pipelineId);
    }

    jdbc.update(
        """
        update crm_pipelines
        set name = ?, description = ?, display_order = coalesce(?, display_order), updated_at = ?
        where company_id = ? and id = ?
        """,
        name,
        input.description() != null ? input.description().trim() : existing.description(),
        input.displayOrder(),
        now,
        companyId,
        pipelineId);

    if (input.stages() != null && !input.stages().isEmpty()) {
      saveStages(companyId, pipelineId, input.stages(), now);
    }

    return get(companyId, pipelineId);
  }

  @Transactional
  public void delete(long companyId, long pipelineId, long actorUserId) {
    PipelineDto existing = get(companyId, pipelineId);
    if (existing.isDefault()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DELETE_DEFAULT_PIPELINE");
    }

    Integer dealCount =
        jdbc.queryForObject(
            "select count(*) from crm_deals where company_id = ? and pipeline_id = ?",
            Integer.class,
            companyId,
            pipelineId);
    if (dealCount != null && dealCount > 0) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "PIPELINE_HAS_DEALS: Cannot delete pipeline with "
              + dealCount
              + " active deals. Please reassign them first.");
    }

    jdbc.update("delete from crm_pipelines where company_id = ? and id = ?", companyId, pipelineId);
  }

  private void saveStages(long companyId, long pipelineId, List<StageInput> stages, Timestamp now) {
    // Delete existing stages for this pipeline and recreate cleanly
    jdbc.update(
        "delete from crm_pipeline_stages where company_id = ? and pipeline_id = ?",
        companyId,
        pipelineId);

    int order = 1;
    for (StageInput s : stages) {
      String key = s.key() != null ? s.key().trim().toUpperCase(Locale.ROOT) : "";
      if (key.isBlank()) {
        key = "STAGE_" + order;
      }
      String stageName = s.name() != null ? s.name().trim() : key;
      String category =
          s.category() != null ? s.category().trim().toUpperCase(Locale.ROOT) : "OPEN";
      if (!List.of("OPEN", "WON", "LOST").contains(category)) {
        category = "OPEN";
      }
      int prob =
          s.probability() != null
              ? Math.max(0, Math.min(100, s.probability()))
              : (category.equals("WON") ? 100 : (category.equals("LOST") ? 0 : 20));
      String color = s.color() != null && !s.color().isBlank() ? s.color().trim() : "indigo";

      long stageId = IdGenerator.nextId();
      jdbc.update(
          """
          insert into crm_pipeline_stages
            (id, company_id, pipeline_id, key, name, category, probability, display_order, color, created_at, updated_at)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          stageId,
          companyId,
          pipelineId,
          key,
          stageName,
          category,
          prob,
          order++,
          color,
          now,
          now);
    }
  }

  private void createStandardStages(long companyId, long pipelineId, Timestamp now) {
    List<StageDto> defaults =
        List.of(
            new StageDto("", "NEW", "New", "OPEN", 10, 1, "sky"),
            new StageDto("", "QUALIFIED", "Qualified", "OPEN", 25, 2, "indigo"),
            new StageDto("", "PROPOSAL", "Proposal", "OPEN", 50, 3, "violet"),
            new StageDto("", "NEGOTIATION", "Negotiation", "OPEN", 75, 4, "amber"),
            new StageDto("", "WON", "Won", "WON", 100, 5, "emerald"),
            new StageDto("", "LOST", "Lost", "LOST", 0, 6, "rose"));

    for (StageDto s : defaults) {
      long stageId = IdGenerator.nextId();
      jdbc.update(
          """
          insert into crm_pipeline_stages
            (id, company_id, pipeline_id, key, name, category, probability, display_order, color, created_at, updated_at)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          on conflict do nothing
          """,
          stageId,
          companyId,
          pipelineId,
          s.key(),
          s.name(),
          s.category(),
          s.probability(),
          s.displayOrder(),
          s.color(),
          now,
          now);
    }
  }

  private static class PipelineBuilder {
    final String id;
    final String name;
    final String description;
    final boolean isDefault;
    final int displayOrder;
    final List<StageDto> stages = new ArrayList<>();

    PipelineBuilder(
        String id, String name, String description, boolean isDefault, int displayOrder) {
      this.id = id;
      this.name = name;
      this.description = description;
      this.isDefault = isDefault;
      this.displayOrder = displayOrder;
    }

    PipelineDto build() {
      return new PipelineDto(id, name, description, isDefault, displayOrder, List.copyOf(stages));
    }
  }
}

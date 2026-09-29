package com.messaging.channel.meta.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppSendRequest;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppTemplateResponse;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppTemplateSyncResponse;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.id.IdGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaWhatsAppTemplateService {
  private static final Pattern VARIABLE = Pattern.compile("\\{\\{(\\d+)}}");

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final ChannelConnectionRepository connectionRepository;
  private final MetaWhatsAppCredentialResolver credentialResolver;
  private final MetaWhatsAppRuntimeGraphClient graphClient;
  private final MetaWhatsAppRuntimeProperties properties;

  public List<MetaWhatsAppTemplateResponse> list(long companyId, long connectionId) {
    requireConnection(companyId, connectionId);
    return jdbcTemplate.query(
        """
        select id,provider_template_id,name,language,category,status,components_json::text,synced_at
        from meta_whatsapp_template_cache
        where company_id=? and connection_id=?
        order by name,language
        """,
        (rs, rowNum) ->
            new MetaWhatsAppTemplateResponse(
                String.valueOf(rs.getLong("id")),
                rs.getString("provider_template_id"),
                rs.getString("name"),
                rs.getString("language"),
                rs.getString("category"),
                rs.getString("status"),
                readJson(rs.getString("components_json")),
                rs.getTimestamp("synced_at").toInstant()),
        companyId,
        connectionId);
  }

  public MetaWhatsAppTemplateSyncResponse sync(long companyId, long connectionId) {
    requireConnection(companyId, connectionId);
    var credential = credentialResolver.require(connectionId);
    var templates = graphClient.listTemplates(credential);
    Instant syncedAt = Instant.now();
    int approved = 0;
    int rejected = 0;
    for (var template : templates) {
      String status = normalize(template.status());
      if ("APPROVED".equals(status)) approved++;
      else if ("REJECTED".equals(status)) rejected++;
      jdbcTemplate.update(
          """
          insert into meta_whatsapp_template_cache
            (id,company_id,connection_id,provider_template_id,name,language,category,status,
             components_json,synced_at,updated_at)
          values (?,?,?,?,?,?,?,?,cast(? as jsonb),?,?)
          on conflict (connection_id,name,language)
          do update set provider_template_id=excluded.provider_template_id,
                        category=excluded.category,status=excluded.status,
                        components_json=excluded.components_json,
                        synced_at=excluded.synced_at,updated_at=excluded.updated_at
          """,
          IdGenerator.nextId(),
          companyId,
          connectionId,
          template.providerTemplateId(),
          template.name(),
          template.language(),
          template.category(),
          status,
          template.components().toString(),
          Timestamp.from(syncedAt),
          Timestamp.from(syncedAt));
    }
    return new MetaWhatsAppTemplateSyncResponse(
        templates.size(), approved, rejected, syncedAt);
  }

  public boolean applyWebhookStatus(String payloadJson) {
    JsonNode root = readJson(payloadJson);
    JsonNode value = root.path("value");
    String providerTemplateId = text(value, "message_template_id");
    String name = text(value, "message_template_name");
    String language = text(value, "message_template_language");
    String status = normalize(text(value, "event"));
    if ("UNKNOWN".equals(status)) return false;

    int updated = 0;
    if (providerTemplateId != null) {
      updated =
          jdbcTemplate.update(
              """
              update meta_whatsapp_template_cache
              set status=?,updated_at=now()
              where provider_template_id=?
              """,
              status,
              providerTemplateId);
    }
    if (updated == 0 && name != null && language != null) {
      updated =
          jdbcTemplate.update(
              """
              update meta_whatsapp_template_cache
              set status=?,updated_at=now()
              where name=? and language=?
              """,
              status,
              name,
              language);
    }
    return updated > 0;
  }

  public PreparedTemplate prepare(
      long companyId,
      long connectionId,
      String templateId,
      MetaWhatsAppSendRequest.Parameters supplied) {
    long id = parseId(templateId, "templateId");
    List<CachedTemplate> rows =
        jdbcTemplate.query(
            """
            select id,name,language,status,components_json::text,synced_at
            from meta_whatsapp_template_cache
            where id=? and company_id=? and connection_id=?
            """,
            (rs, rowNum) ->
                new CachedTemplate(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("language"),
                    rs.getString("status"),
                    rs.getString("components_json"),
                    rs.getTimestamp("synced_at").toInstant()),
            id,
            companyId,
            connectionId);
    if (rows.isEmpty()) throw new NotFoundException("WhatsApp template not found");
    CachedTemplate template = rows.getFirst();
    if (!"APPROVED".equals(template.status())
        || template.syncedAt().isBefore(Instant.now().minus(properties.getTemplateMaxAge()))) {
      throw new ConflictException("WHATSAPP_TEMPLATE_NOT_APPROVED");
    }

    JsonNode components = readJson(template.componentsJson());
    MetaWhatsAppSendRequest.Parameters params =
        supplied == null
            ? new MetaWhatsAppSendRequest.Parameters(List.of(), List.of(), List.of())
            : supplied;
    Counts expected = expected(components);
    if (params.header().size() != expected.header()
        || params.body().size() != expected.body()
        || params.buttons().size() != expected.buttons()) {
      throw new BadRequestException("WHATSAPP_TEMPLATE_PARAMETERS_INVALID");
    }

    List<Map<String, Object>> providerComponents = new ArrayList<>();
    addTextComponent(providerComponents, "header", params.header());
    addTextComponent(providerComponents, "body", params.body());
    addButtonComponents(providerComponents, components, params.buttons());
    return new PreparedTemplate(
        template.id(), template.name(), template.language(), List.copyOf(providerComponents));
  }

  private void addTextComponent(
      List<Map<String, Object>> output, String type, List<String> values) {
    if (values.isEmpty()) return;
    output.add(
        Map.of(
            "type",
            type,
            "parameters",
            values.stream().map(value -> Map.<String, Object>of("type", "text", "text", value)).toList()));
  }

  private void addButtonComponents(
      List<Map<String, Object>> output, JsonNode components, List<String> values) {
    if (values.isEmpty()) return;
    int valueIndex = 0;
    for (JsonNode component : components) {
      if (!"BUTTONS".equalsIgnoreCase(component.path("type").asText())) continue;
      JsonNode buttons = component.path("buttons");
      if (!buttons.isArray()) continue;
      for (int index = 0; index < buttons.size() && valueIndex < values.size(); index++) {
        JsonNode button = buttons.get(index);
        String source = button.toString();
        if (countVariables(source) == 0) continue;
        String subtype =
            "QUICK_REPLY".equalsIgnoreCase(button.path("type").asText()) ? "quick_reply" : "url";
        output.add(
            Map.of(
                "type", "button",
                "sub_type", subtype,
                "index", String.valueOf(index),
                "parameters",
                    List.of(Map.of("type", "text", "text", values.get(valueIndex++)))));
      }
    }
  }

  private Counts expected(JsonNode components) {
    int header = 0;
    int body = 0;
    int buttons = 0;
    if (components.isArray()) {
      for (JsonNode component : components) {
        String type = component.path("type").asText("");
        if ("HEADER".equalsIgnoreCase(type)) header += countVariables(component.toString());
        else if ("BODY".equalsIgnoreCase(type)) body += countVariables(component.toString());
        else if ("BUTTONS".equalsIgnoreCase(type)) {
          JsonNode list = component.path("buttons");
          if (list.isArray()) {
            for (JsonNode button : list) {
              if (countVariables(button.toString()) > 0) buttons++;
            }
          }
        }
      }
    }
    return new Counts(header, body, buttons);
  }

  private int countVariables(String text) {
    int max = 0;
    Matcher matcher = VARIABLE.matcher(text == null ? "" : text);
    while (matcher.find()) max = Math.max(max, Integer.parseInt(matcher.group(1)));
    return max;
  }

  private void requireConnection(long companyId, long connectionId) {
    var connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("WhatsApp connection not found"));
    if (connection.getProviderType() != ProviderType.META_WHATSAPP) {
      throw new NotFoundException("WhatsApp connection not found");
    }
  }

  private long parseId(String value, String field) {
    try {
      return Long.parseLong(value);
    } catch (Exception exception) {
      throw new BadRequestException("Invalid " + field);
    }
  }

  private String text(JsonNode node, String field) {
    if (node == null) return null;
    JsonNode value = node.get(field);
    return value == null || !value.isValueNode() || value.asText().isBlank()
        ? null
        : value.asText().trim();
  }

  private JsonNode readJson(String value) {
    try {
      return objectMapper.readTree(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Stored WhatsApp template JSON is invalid", exception);
    }
  }

  private String normalize(String value) {
    return value == null ? "UNKNOWN" : value.trim().toUpperCase(java.util.Locale.ROOT);
  }

  private record CachedTemplate(
      long id, String name, String language, String status, String componentsJson, Instant syncedAt) {}
  private record Counts(int header, int body, int buttons) {}

  public record PreparedTemplate(
      long id, String name, String language, List<Map<String, Object>> providerComponents) {}
}

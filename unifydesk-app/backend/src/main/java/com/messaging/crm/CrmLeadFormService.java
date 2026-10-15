package com.messaging.crm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrmLeadFormService {

  private final JdbcTemplate jdbc;
  private final CrmCustomFieldService crmCustomFields;
  private final CrmAutomationService crmAutomationService;
  private final CrmLeadScoringService crmLeadScoringService;

  @org.springframework.beans.factory.annotation.Autowired
  public CrmLeadFormService(
      JdbcTemplate jdbc,
      @org.springframework.beans.factory.annotation.Autowired(required = false)
      CrmCustomFieldService crmCustomFields,
      @org.springframework.beans.factory.annotation.Autowired(required = false)
      CrmAutomationService crmAutomationService,
      @org.springframework.beans.factory.annotation.Autowired(required = false)
      CrmLeadScoringService crmLeadScoringService) {
    this.jdbc = jdbc;
    this.crmCustomFields = crmCustomFields;
    this.crmAutomationService = crmAutomationService;
    this.crmLeadScoringService = crmLeadScoringService;
  }

  public CrmLeadFormService(JdbcTemplate jdbc) {
    this(jdbc, null, null, null);
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<FormFieldConfig>> FIELDS_REF = new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

  public record FormFieldConfig(
      String fieldKey,
      String label,
      String type,
      boolean required,
      String placeholder,
      List<String> options) {}

  public record LeadFormDto(
      String id,
      String formKey,
      String title,
      String description,
      String status,
      String defaultLeadStage,
      String assigneeUserId,
      String assigneeName,
      String successMessage,
      String redirectUrl,
      List<FormFieldConfig> fields,
      long submissionsCount,
      Instant createdAt,
      Instant updatedAt) {}

  public record PublicFormDto(
      String formKey,
      String title,
      String description,
      String successMessage,
      String redirectUrl,
      List<FormFieldConfig> fields) {}

  public record FormSubmissionDto(
      String id,
      String formId,
      String contactId,
      String contactName,
      Map<String, Object> payload,
      String ipAddress,
      Instant submittedAt) {}

  public record SubmitResult(
      boolean success,
      String message,
      String redirectUrl,
      String contactId) {}

  public record CreateFormInput(
      String title,
      String description,
      String defaultLeadStage,
      Long assigneeUserId,
      String successMessage,
      String redirectUrl,
      List<FormFieldConfig> fields) {}

  public record UpdateFormInput(
      String title,
      String description,
      String status,
      String defaultLeadStage,
      Long assigneeUserId,
      String successMessage,
      String redirectUrl,
      List<FormFieldConfig> fields) {}

  @Transactional(readOnly = true)
  public List<LeadFormDto> list(long companyId) {
    String sql =
        """
        select f.*, u.name as assignee_name
        from crm_lead_forms f
        left join users u on u.id = f.assignee_user_id
        where f.company_id = ?
        order by f.created_at desc
        """;
    return jdbc.query(sql, this::mapForm, companyId);
  }

  @Transactional(readOnly = true)
  public LeadFormDto get(long companyId, long formId) {
    String sql =
        """
        select f.*, u.name as assignee_name
        from crm_lead_forms f
        left join users u on u.id = f.assignee_user_id
        where f.company_id = ? and f.id = ?
        """;
    var rows = jdbc.query(sql, this::mapForm, companyId, formId);
    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "CRM_FORM_NOT_FOUND");
    return rows.getFirst();
  }

  @Transactional
  public LeadFormDto create(long companyId, CreateFormInput input, long actorUserId) {
    if (input.title() == null || input.title().trim().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "FORM_TITLE_REQUIRED");
    }

    String formKey = "form_" + UUID.randomUUID().toString().replace("-", "");
    long formId = IdGenerator.nextId();
    Timestamp now = Timestamp.from(Instant.now());

    List<FormFieldConfig> fields =
        input.fields() != null && !input.fields().isEmpty()
            ? input.fields()
            : defaultFields();

    String fieldsJson;
    try {
      fieldsJson = MAPPER.writeValueAsString(fields);
    } catch (Exception e) {
      fieldsJson = "[]";
    }

    jdbc.update(
        """
        insert into crm_lead_forms
          (id, company_id, form_key, title, description, status, default_lead_stage,
           assignee_user_id, success_message, redirect_url, fields_config, submissions_count, created_at, updated_at)
        values (?, ?, ?, ?, ?, 'ACTIVE', coalesce(?, 'NEW'), ?,
                coalesce(?, 'Thank you! We will get in touch with you shortly.'), ?, cast(? as jsonb), 0, ?, ?)
        """,
        formId,
        companyId,
        formKey,
        input.title().trim(),
        input.description() != null ? input.description().trim() : null,
        input.defaultLeadStage() != null ? input.defaultLeadStage().trim().toUpperCase(Locale.ROOT) : "NEW",
        input.assigneeUserId(),
        input.successMessage() != null ? input.successMessage().trim() : null,
        normalizeRedirectUrl(input.redirectUrl()),
        fieldsJson,
        now,
        now);

    return get(companyId, formId);
  }

  @Transactional
  public LeadFormDto update(long companyId, long formId, UpdateFormInput input, long actorUserId) {
    LeadFormDto existing = get(companyId, formId);

    String title = input.title() != null ? input.title().trim() : existing.title();
    if (title.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "FORM_TITLE_INVALID");

    String status = input.status() != null ? input.status().trim().toUpperCase(Locale.ROOT) : existing.status();
    if (!List.of("ACTIVE", "PAUSED").contains(status)) status = "ACTIVE";

    Timestamp now = Timestamp.from(Instant.now());

    String fieldsJson = null;
    if (input.fields() != null) {
      try {
        fieldsJson = MAPPER.writeValueAsString(input.fields());
      } catch (Exception e) {
        fieldsJson = "[]";
      }
    }

    jdbc.update(
        """
        update crm_lead_forms set
          title = ?,
          description = ?,
          status = ?,
          default_lead_stage = coalesce(?, default_lead_stage),
          assignee_user_id = ?,
          success_message = coalesce(?, success_message),
          redirect_url = ?,
          fields_config = coalesce(cast(? as jsonb), fields_config),
          updated_at = ?
        where company_id = ? and id = ?
        """,
        title,
        input.description() != null ? input.description().trim() : existing.description(),
        status,
        input.defaultLeadStage(),
        input.assigneeUserId(),
        input.successMessage(),
        input.redirectUrl() != null ? normalizeRedirectUrl(input.redirectUrl()) : existing.redirectUrl(),
        fieldsJson,
        now,
        companyId,
        formId);

    return get(companyId, formId);
  }

  @Transactional
  public void delete(long companyId, long formId, long actorUserId) {
    get(companyId, formId);
    jdbc.update("delete from crm_lead_forms where company_id = ? and id = ?", companyId, formId);
  }

  @Transactional(readOnly = true)
  public List<FormSubmissionDto> submissions(long companyId, long formId, int limit) {
    get(companyId, formId);
    int safeLimit = Math.max(1, Math.min(limit, 200));

    String sql =
        """
        select s.*, c.display_name as contact_name
        from crm_form_submissions s
        left join contacts c on c.company_id = s.company_id and c.id = s.contact_id
        where s.company_id = ? and s.form_id = ?
        order by s.submitted_at desc limit ?
        """;

    return jdbc.query(
        sql,
        (rs, row) -> {
          Map<String, Object> payload = Map.of();
          String raw = rs.getString("payload");
          if (raw != null && !raw.isBlank()) {
            try {
              payload = MAPPER.readValue(raw, MAP_REF);
            } catch (Exception ignored) {}
          }
          return new FormSubmissionDto(
              String.valueOf(rs.getLong("id")),
              String.valueOf(rs.getLong("form_id")),
              rs.getObject("contact_id") != null ? String.valueOf(rs.getLong("contact_id")) : null,
              rs.getString("contact_name"),
              payload,
              rs.getString("ip_address"),
              rs.getTimestamp("submitted_at").toInstant());
        },
        companyId,
        formId,
        safeLimit);
  }

  // PUBLIC FORM API (No Authentication Required)
  @Transactional(readOnly = true)
  public PublicFormDto getPublicForm(String formKey) {
    String sql =
        """
        select form_key, title, description, success_message, redirect_url, fields_config, status
        from crm_lead_forms
        where form_key = ?
        """;
    var rows =
        jdbc.query(
            sql,
            (rs, row) -> {
              if (!"ACTIVE".equalsIgnoreCase(rs.getString("status"))) {
                throw new ApiException(HttpStatus.GONE, "FORM_INACTIVE");
              }
              List<FormFieldConfig> fields = List.of();
              String raw = rs.getString("fields_config");
              if (raw != null && !raw.isBlank()) {
                try {
                  fields = MAPPER.readValue(raw, FIELDS_REF);
                } catch (Exception ignored) {}
              }
              return new PublicFormDto(
                  rs.getString("form_key"),
                  rs.getString("title"),
                  rs.getString("description"),
                  rs.getString("success_message"),
                  normalizeRedirectUrl(rs.getString("redirect_url")),
                  fields);
            },
            formKey);

    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "FORM_NOT_FOUND");
    return rows.getFirst();
  }

  @Transactional
  public SubmitResult submitPublicForm(
      String formKey, Map<String, Object> data, String ipAddress, String userAgent) {
    String sql =
        """
        select id, company_id, title, status, default_lead_stage, assignee_user_id, success_message, redirect_url, fields_config
        from crm_lead_forms
        where form_key = ?
        """;
    var formRows =
        jdbc.query(
            sql,
            (rs, row) ->
                new FormRecord(
                    rs.getLong("id"),
                    rs.getLong("company_id"),
                    rs.getString("title"),
                    rs.getString("status"),
                    rs.getString("default_lead_stage"),
                    rs.getObject("assignee_user_id") != null ? rs.getLong("assignee_user_id") : null,
                    rs.getString("success_message"),
                    rs.getString("redirect_url"),
                    rs.getString("fields_config")),
            formKey);

    if (formRows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "FORM_NOT_FOUND");
    FormRecord form = formRows.getFirst();

    if (!"ACTIVE".equalsIgnoreCase(form.status())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "FORM_NOT_ACTIVE");
    }

    List<FormFieldConfig> formFields = List.of();
    if (form.fieldsConfig() != null && !form.fieldsConfig().isBlank()) {
      try {
        formFields = MAPPER.readValue(form.fieldsConfig(), FIELDS_REF);
      } catch (Exception ignored) {}
    }

    // Validate required fields explicitly configured on this form
    for (FormFieldConfig f : formFields) {
      if (f.required()) {
        Object val = data != null ? data.get(f.fieldKey()) : null;
        if (val == null || (val instanceof String s && s.trim().isEmpty())) {
          String label = f.label() != null && !f.label().isBlank() ? f.label() : f.fieldKey();
          throw new ApiException(HttpStatus.BAD_REQUEST, "FIELD_REQUIRED:" + label);
        }
      }
    }

    long companyId = form.companyId();
    Timestamp now = Timestamp.from(Instant.now());

    // Extract core attributes
    String email = extractString(data, "email", "primaryEmail", "mail");
    String phone = extractString(data, "phone", "phoneNumber", "mobile", "tel");
    String fullName = extractString(data, "name", "fullName", "displayName");
    String firstName = extractString(data, "firstName", "first_name");
    String lastName = extractString(data, "lastName", "last_name");
    String org = extractString(data, "company", "organization", "companyName", "org");
    String message = extractString(data, "message", "notes", "comment", "inquiry");

    if (fullName == null || fullName.isBlank()) {
      if (firstName != null && !firstName.isBlank()) {
        fullName = (firstName + " " + (lastName != null ? lastName : "")).trim();
      }
    }
    if ((fullName == null || fullName.isBlank()) && email != null) {
      fullName = email.split("@")[0];
    }
    if (fullName == null || fullName.isBlank()) {
      fullName = "Website Visitor";
    }

    // Try resolving existing contact by email or phone
    Long contactId = null;
    if (email != null && !email.isBlank()) {
      String normEmail = email.trim().toLowerCase(Locale.ROOT);
      List<Long> found =
          jdbc.query(
              """
              select contact_id from contact_identities
              where company_id = ? and identity_type = 'EMAIL' and normalized_value = ? and status = 'ACTIVE'
              limit 1
              """,
              (rs, row) -> rs.getLong("contact_id"),
              companyId,
              normEmail);
      if (!found.isEmpty()) contactId = found.getFirst();
    }

    if (contactId == null && phone != null && !phone.isBlank()) {
      String cleanPhone = phone.replaceAll("[^0-9]", "");
      if (!cleanPhone.isBlank()) {
        List<Long> found =
            jdbc.query(
                """
                select contact_id from contact_identities
                where company_id = ? and identity_type = 'PHONE' and normalized_value = ? and status = 'ACTIVE'
                limit 1
                """,
                (rs, row) -> rs.getLong("contact_id"),
                companyId,
                cleanPhone);
        if (!found.isEmpty()) contactId = found.getFirst();
      }
    }

    // Determine creator / owner user for the contact
    Long creatorUserId = form.assigneeUserId();
    if (creatorUserId == null) {
      List<Long> members =
          jdbc.query(
              """
              select user_id from company_members
              where company_id = ? and status = 'ACTIVE'
              order by case when role = 'OWNER' then 0 when role = 'ADMIN' then 1 else 2 end, created_at asc
              limit 1
              """,
              (rs, row) -> rs.getLong("user_id"),
              companyId);
      if (!members.isEmpty()) {
        creatorUserId = members.getFirst();
      }
    }
    if (creatorUserId == null) {
      List<Long> anyMembers =
          jdbc.query(
              "select user_id from company_members where company_id = ? limit 1",
              (rs, row) -> rs.getLong("user_id"),
              companyId);
      if (!anyMembers.isEmpty()) {
        creatorUserId = anyMembers.getFirst();
      }
    }
    if (creatorUserId == null) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "COMPANY_OWNER_NOT_FOUND");
    }

    // Create new contact if not matched
    if (contactId == null) {
      contactId = IdGenerator.nextId();
      jdbc.update(
          """
          insert into contacts
            (id, company_id, first_name, last_name, display_name, organization, source, notes, owner_user_id, status, created_by_user_id, created_at, updated_at, version)
          values (?, ?, ?, ?, ?, ?, 'WEB_FORM', ?, ?, 'ACTIVE', ?, ?, ?, 0)
          """,
          contactId,
          companyId,
          firstName,
          lastName,
          fullName,
          org,
          "Created from web form: " + form.title(),
          form.assigneeUserId(),
          creatorUserId,
          now,
          now);

      if (email != null && !email.isBlank()) {
        String normEmail = email.trim().toLowerCase(Locale.ROOT);
        jdbc.update(
            """
            insert into contact_identities
              (id, company_id, contact_id, identity_type, value, normalized_value, is_primary, status, created_at, updated_at)
            values (?, ?, ?, 'EMAIL', ?, ?, true, 'ACTIVE', ?, ?)
            """,
            IdGenerator.nextId(),
            companyId,
            contactId,
            email.trim(),
            normEmail,
            now,
            now);
      }

      if (phone != null && !phone.isBlank()) {
        String cleanPhone = phone.replaceAll("[^0-9]", "");
        jdbc.update(
            """
            insert into contact_identities
              (id, company_id, contact_id, identity_type, value, normalized_value, is_primary, status, created_at, updated_at)
            values (?, ?, ?, 'PHONE', ?, ?, true, 'ACTIVE', ?, ?)
            """,
            IdGenerator.nextId(),
            companyId,
            contactId,
            phone.trim(),
            cleanPhone,
            now,
            now);
      }
    }

    // Custom fields extraction: exclude standard contact and message attributes
    Map<String, Object> customMap = new HashMap<>();
    java.util.Set<String> coreKeys = java.util.Set.of(
        "email", "primaryemail", "mail",
        "phone", "phonenumber", "mobile", "tel",
        "name", "fullname", "displayname",
        "firstname", "first_name",
        "lastname", "last_name",
        "company", "organization", "companyname", "org",
        "message", "notes", "comment", "inquiry"
    );
    if (data != null) {
      for (Map.Entry<String, Object> entry : data.entrySet()) {
        String k = entry.getKey();
        if (k == null) continue;
        if (!coreKeys.contains(k.toLowerCase(Locale.ROOT)) && entry.getValue() != null) {
          String s = entry.getValue().toString().trim();
          if (!s.isEmpty()) {
            customMap.put(k, entry.getValue());
          }
        }
      }
    }

    Map<String, Object> validatedCustom =
        (crmCustomFields != null && !customMap.isEmpty())
            ? crmCustomFields.validateValues(companyId, CrmCustomFieldService.EntityType.LEAD, customMap, false)
            : Map.of();

    String customJson;
    try {
      customJson = MAPPER.writeValueAsString(validatedCustom);
    } catch (Exception e) {
      customJson = "{}";
    }

    // Enroll in crm_leads or update
    String stage = form.defaultLeadStage() != null ? form.defaultLeadStage() : "NEW";

    jdbc.update(
        """
        insert into crm_leads
          (company_id, contact_id, stage, custom_fields, created_by_user_id, updated_by_user_id, created_at, updated_at)
        values (?, ?, ?, cast(? as jsonb), ?, ?, ?, ?)
        on conflict (company_id, contact_id) do update
          set updated_at = excluded.updated_at
        """,
        companyId,
        contactId,
        stage,
        customJson,
        creatorUserId,
        creatorUserId,
        now,
        now);

    // Record activity note
    String noteBody = "Submitted Web Form: " + form.title();
    if (message != null && !message.isBlank()) {
      noteBody += "\nMessage: " + message.trim();
    }
    jdbc.update(
        """
        insert into crm_notes (id, company_id, contact_id, body, actor_user_id, occurred_at)
        values (?, ?, ?, ?, ?, ?)
        """,
        IdGenerator.nextId(),
        companyId,
        contactId,
        noteBody,
        creatorUserId,
        now);

    // Record submission payload
    long submissionId = IdGenerator.nextId();
    String payloadJson;
    try {
      payloadJson = MAPPER.writeValueAsString(data);
    } catch (Exception e) {
      payloadJson = "{}";
    }

    jdbc.update(
        """
        insert into crm_form_submissions
          (id, company_id, form_id, contact_id, payload, ip_address, user_agent, submitted_at)
        values (?, ?, ?, ?, cast(? as jsonb), ?, ?, ?)
        """,
        submissionId,
        companyId,
        form.id(),
        contactId,
        payloadJson,
        ipAddress,
        userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 497) + "..." : userAgent,
        now);

    // Increment submissions count
    jdbc.update(
        "update crm_lead_forms set submissions_count = submissions_count + 1, updated_at = ? where id = ?",
        now,
        form.id());

    if (crmAutomationService != null) {
      try {
        crmAutomationService.fireEvent(
            companyId,
            "FORM_SUBMITTED",
            "FORM_SUBMISSION",
            String.valueOf(contactId),
            Map.of(
                "formId", form.id(),
                "formKey", formKey,
                "formTitle", form.title(),
                "contactId", contactId));
      } catch (Exception ignored) {}
    }

    if (crmLeadScoringService != null) {
      try {
        crmLeadScoringService.calculateScore(companyId, contactId);
      } catch (Exception ignored) {}
    }

    return new SubmitResult(true, form.successMessage(), normalizeRedirectUrl(form.redirectUrl()), String.valueOf(contactId));
  }

  private LeadFormDto mapForm(ResultSet rs, int row) throws SQLException {
    List<FormFieldConfig> fields = List.of();
    String raw = rs.getString("fields_config");
    if (raw != null && !raw.isBlank()) {
      try {
        fields = MAPPER.readValue(raw, FIELDS_REF);
      } catch (Exception ignored) {}
    }
    return new LeadFormDto(
        String.valueOf(rs.getLong("id")),
        rs.getString("form_key"),
        rs.getString("title"),
        rs.getString("description"),
        rs.getString("status"),
        rs.getString("default_lead_stage"),
        rs.getObject("assignee_user_id") != null ? String.valueOf(rs.getLong("assignee_user_id")) : null,
        rs.getString("assignee_name"),
        rs.getString("success_message"),
        rs.getString("redirect_url"),
        fields,
        rs.getLong("submissions_count"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private static List<FormFieldConfig> defaultFields() {
    return List.of(
        new FormFieldConfig("fullName", "Full Name", "text", true, "John Doe", List.of()),
        new FormFieldConfig("email", "Business Email", "email", true, "john@company.com", List.of()),
        new FormFieldConfig("phone", "Phone Number", "tel", false, "+1 555 123 4567", List.of()),
        new FormFieldConfig("organization", "Company Name", "text", false, "Acme Inc.", List.of()),
        new FormFieldConfig("message", "How can we help?", "textarea", false, "Tell us about your project...", List.of()));
  }

  private static String extractString(Map<String, Object> data, String... keys) {
    for (String k : keys) {
      Object val = data.get(k);
      if (val instanceof String s && !s.isBlank()) return s.trim();
    }
    return null;
  }

  private static String normalizeRedirectUrl(String url) {
    if (url == null || url.trim().isEmpty()) {
      return null;
    }
    String trimmed = url.trim();
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("/")) {
      return trimmed;
    }
    return "https://" + trimmed;
  }

  private record FormRecord(
      long id,
      long companyId,
      String title,
      String status,
      String defaultLeadStage,
      Long assigneeUserId,
      String successMessage,
      String redirectUrl,
      String fieldsConfig) {}
}

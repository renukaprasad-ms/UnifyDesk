package com.messaging.crm;

import com.messaging.common.validation.ValidPhone;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrmBookingService {
  private static final Logger log = LoggerFactory.getLogger(CrmBookingService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> STRING_LIST_REF = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final CrmWorkService workService;

  public CrmBookingService(JdbcTemplate jdbc, CrmWorkService workService) {
    this.jdbc = jdbc;
    this.workService = workService;
  }

  public record BookingTypeDto(
      String id,
      String companyId,
      String slug,
      String title,
      String description,
      int durationMinutes,
      String locationType,
      String locationDetails,
      String hostUserId,
      String hostName,
      boolean isActive,
      String workingHoursStart,
      String workingHoursEnd,
      List<String> daysOfWeek,
      int bufferBeforeMinutes,
      int bufferAfterMinutes,
      Instant createdAt,
      Instant updatedAt) {}

  public record MeetingBookingDto(
      String id,
      String companyId,
      String bookingTypeId,
      String bookingTypeTitle,
      String hostUserId,
      String hostName,
      String contactId,
      String guestName,
      String guestEmail,
      String guestPhone,
      String notes,
      Instant startTime,
      Instant endTime,
      String status,
      String meetingLink,
      Instant createdAt) {}

  public record PublicBookingTypeDto(
      String slug,
      String title,
      String description,
      int durationMinutes,
      String locationType,
      String locationDetails,
      String hostName,
      String workingHoursStart,
      String workingHoursEnd,
      List<String> daysOfWeek) {}

  public record SaveBookingTypeInput(
      String title,
      String slug,
      String description,
      Integer durationMinutes,
      String locationType,
      String locationDetails,
      Long hostUserId,
      Boolean isActive,
      String workingHoursStart,
      String workingHoursEnd,
      List<String> daysOfWeek,
      Integer bufferBeforeMinutes,
      Integer bufferAfterMinutes) {}

  public record BookMeetingInput(
      String slotTime, // ISO string
      String name,
      String email,
      @ValidPhone String phone,
      String notes) {}

  public record BookResult(
      boolean success,
      String bookingId,
      String message,
      String meetingLink,
      Instant startTime,
      Instant endTime) {}

  // ================= ADMIN APIS =================

  @Transactional(readOnly = true)
  public List<BookingTypeDto> listBookingTypes(long companyId) {
    String sql =
        """
        select b.*, u.name as host_name
        from crm_booking_types b
        left join users u on u.id = b.host_user_id
        where b.company_id = ?
        order by b.created_at desc
        """;
    return jdbc.query(sql, (rs, rowNum) -> mapBookingType(rs, rowNum), companyId);
  }

  @Transactional(readOnly = true)
  public BookingTypeDto getBookingType(long companyId, long id) {
    String sql =
        """
        select b.*, u.name as host_name
        from crm_booking_types b
        left join users u on u.id = b.host_user_id
        where b.company_id = ? and b.id = ?
        """;
    List<BookingTypeDto> list =
        jdbc.query(sql, (rs, rowNum) -> mapBookingType(rs, rowNum), companyId, id);
    if (list.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_TYPE_NOT_FOUND");
    }
    return list.getFirst();
  }

  @Transactional
  public BookingTypeDto createBookingType(
      long companyId, long actorUserId, SaveBookingTypeInput input) {
    if (input == null || input.title() == null || input.title().trim().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BOOKING_TITLE_REQUIRED");
    }

    String slug = generateSlug(companyId, input.slug(), input.title());
    int duration =
        input.durationMinutes() != null && input.durationMinutes() > 0
            ? input.durationMinutes()
            : 30;
    long hostId = input.hostUserId() != null ? input.hostUserId() : actorUserId;
    String locationType =
        input.locationType() != null ? input.locationType().trim().toUpperCase() : "GOOGLE_MEET";
    String locationDetails =
        input.locationDetails() != null ? input.locationDetails().trim() : null;
    String start = input.workingHoursStart() != null ? input.workingHoursStart().trim() : "09:00";
    String end = input.workingHoursEnd() != null ? input.workingHoursEnd().trim() : "18:00";
    List<String> days =
        input.daysOfWeek() != null && !input.daysOfWeek().isEmpty()
            ? input.daysOfWeek()
            : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY");

    long id = IdGenerator.nextId();
    String daysJson = toJson(days);
    boolean active = input.isActive() == null || input.isActive();

    jdbc.update(
        """
        insert into crm_booking_types (
            id, company_id, slug, title, description, duration_minutes,
            location_type, location_details, host_user_id, is_active,
            working_hours_start, working_hours_end, days_of_week,
            buffer_before_minutes, buffer_after_minutes, created_at, updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, now(), now())
        """,
        id,
        companyId,
        slug,
        input.title().trim(),
        input.description() != null ? input.description().trim() : null,
        duration,
        locationType,
        locationDetails,
        hostId,
        active,
        start,
        end,
        daysJson,
        input.bufferBeforeMinutes() != null ? input.bufferBeforeMinutes() : 0,
        input.bufferAfterMinutes() != null ? input.bufferAfterMinutes() : 10);

    return getBookingType(companyId, id);
  }

  @Transactional
  public BookingTypeDto updateBookingType(long companyId, long id, SaveBookingTypeInput input) {
    BookingTypeDto existing = getBookingType(companyId, id);

    String title =
        input.title() != null && !input.title().trim().isEmpty()
            ? input.title().trim()
            : existing.title();
    int duration =
        input.durationMinutes() != null && input.durationMinutes() > 0
            ? input.durationMinutes()
            : existing.durationMinutes();
    long hostId =
        input.hostUserId() != null ? input.hostUserId() : Long.parseLong(existing.hostUserId());
    String locationType =
        input.locationType() != null
            ? input.locationType().trim().toUpperCase()
            : existing.locationType();
    String locationDetails =
        input.locationDetails() != null
            ? input.locationDetails().trim()
            : existing.locationDetails();
    boolean active = input.isActive() != null ? input.isActive() : existing.isActive();
    String start =
        input.workingHoursStart() != null
            ? input.workingHoursStart().trim()
            : existing.workingHoursStart();
    String end =
        input.workingHoursEnd() != null
            ? input.workingHoursEnd().trim()
            : existing.workingHoursEnd();
    List<String> days = input.daysOfWeek() != null ? input.daysOfWeek() : existing.daysOfWeek();
    String daysJson = toJson(days);

    jdbc.update(
        """
        update crm_booking_types
        set title = ?, description = ?, duration_minutes = ?,
            location_type = ?, location_details = ?, host_user_id = ?, is_active = ?,
            working_hours_start = ?, working_hours_end = ?, days_of_week = ?::jsonb,
            buffer_before_minutes = ?, buffer_after_minutes = ?, updated_at = now()
        where company_id = ? and id = ?
        """,
        title,
        input.description() != null ? input.description().trim() : existing.description(),
        duration,
        locationType,
        locationDetails,
        hostId,
        active,
        start,
        end,
        daysJson,
        input.bufferBeforeMinutes() != null
            ? input.bufferBeforeMinutes()
            : existing.bufferBeforeMinutes(),
        input.bufferAfterMinutes() != null
            ? input.bufferAfterMinutes()
            : existing.bufferAfterMinutes(),
        companyId,
        id);

    return getBookingType(companyId, id);
  }

  @Transactional
  public BookingTypeDto toggleBookingType(long companyId, long id, boolean active) {
    getBookingType(companyId, id);
    jdbc.update(
        "update crm_booking_types set is_active = ?, updated_at = now() where company_id = ? and id = ?",
        active,
        companyId,
        id);
    return getBookingType(companyId, id);
  }

  @Transactional
  public void deleteBookingType(long companyId, long id) {
    int updated =
        jdbc.update("delete from crm_booking_types where company_id = ? and id = ?", companyId, id);
    if (updated == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_TYPE_NOT_FOUND");
    }
  }

  @Transactional(readOnly = true)
  public List<MeetingBookingDto> listBookings(
      long companyId, Long bookingTypeId, Long contactId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 100));
    String sql =
        """
        select m.*, b.title as booking_type_title, u.name as host_name
        from crm_meeting_bookings m
        join crm_booking_types b on b.id = m.booking_type_id
        left join users u on u.id = m.host_user_id
        where m.company_id = ?
          and (cast(? as bigint) is null or m.booking_type_id = ?)
          and (cast(? as bigint) is null or m.contact_id = ?)
        order by m.start_time desc
        limit ?
        """;
    return jdbc.query(
        sql,
        (rs, rowNum) -> mapMeetingBooking(rs, rowNum),
        companyId,
        bookingTypeId,
        bookingTypeId,
        contactId,
        contactId,
        safeLimit);
  }

  @Transactional
  public void cancelBooking(long companyId, long bookingId) {
    int updated =
        jdbc.update(
            "update crm_meeting_bookings set status = 'CANCELLED', updated_at = now() where company_id = ? and id = ?",
            companyId,
            bookingId);
    if (updated == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND");
    }
  }

  // ================= PUBLIC BOOKING APIS =================

  @Transactional(readOnly = true)
  public PublicBookingTypeDto getPublicBookingType(String slug) {
    String sql =
        """
        select b.*, u.name as host_name
        from crm_booking_types b
        left join users u on u.id = b.host_user_id
        where b.slug = ? and b.is_active = true
        """;
    var list =
        jdbc.query(
            sql,
            (rs, rowNum) ->
                new PublicBookingTypeDto(
                    rs.getString("slug"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getInt("duration_minutes"),
                    rs.getString("location_type"),
                    rs.getString("location_details"),
                    rs.getString("host_name") != null
                        ? rs.getString("host_name")
                        : "Sales Representative",
                    rs.getString("working_hours_start"),
                    rs.getString("working_hours_end"),
                    fromJsonList(rs.getString("days_of_week"))),
            slug);

    if (list.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_TYPE_NOT_FOUND_OR_INACTIVE");
    }
    return list.getFirst();
  }

  @Transactional(readOnly = true)
  public List<String> getAvailableSlots(String slug, String dateStr) {
    LocalDate date;
    try {
      date = LocalDate.parse(dateStr);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_FORMAT");
    }

    String sql = "select * from crm_booking_types where slug = ? and is_active = true";
    var rows = jdbc.query(sql, (rs, rowNum) -> mapBookingType(rs, rowNum), slug);
    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_TYPE_NOT_FOUND");
    BookingTypeDto bt = rows.getFirst();

    // Check if day of week is supported
    DayOfWeek dow = date.getDayOfWeek();
    if (!bt.daysOfWeek().contains(dow.name())) {
      return List.of();
    }

    // Parse Working Hours
    LocalTime startTime = LocalTime.parse(bt.workingHoursStart());
    LocalTime endTime = LocalTime.parse(bt.workingHoursEnd());

    int duration = bt.durationMinutes();
    int buffer = bt.bufferAfterMinutes();
    int stepMinutes = duration + buffer;

    // Fetch existing confirmed bookings for host on that date
    ZoneId zone = ZoneId.of("UTC");
    ZonedDateTime dayStart = date.atStartOfDay(zone);
    ZonedDateTime dayEnd = date.plusDays(1).atStartOfDay(zone);

    List<ZonedDateTime[]> busyIntervals =
        jdbc.query(
            """
        select start_time, end_time from crm_meeting_bookings
        where host_user_id = ? and status = 'CONFIRMED'
          and start_time >= ? and start_time < ?
        """,
            (rs, rowNum) ->
                new ZonedDateTime[] {
                  rs.getTimestamp("start_time").toInstant().atZone(zone),
                  rs.getTimestamp("end_time").toInstant().atZone(zone)
                },
            Long.parseLong(bt.hostUserId()),
            Timestamp.from(dayStart.toInstant()),
            Timestamp.from(dayEnd.toInstant()));

    List<String> available = new ArrayList<>();
    LocalTime current = startTime;
    Instant minAllowedTime = Instant.now().plus(30, ChronoUnit.MINUTES);

    while (current.plusMinutes(duration).isBefore(endTime)
        || current.plusMinutes(duration).equals(endTime)) {
      ZonedDateTime slotStart = date.atTime(current).atZone(zone);
      ZonedDateTime slotEnd = slotStart.plusMinutes(duration);

      boolean overlaps = false;
      if (slotStart.toInstant().isBefore(minAllowedTime)) {
        overlaps = true;
      } else {
        for (ZonedDateTime[] interval : busyIntervals) {
          if (slotStart.isBefore(interval[1]) && slotEnd.isAfter(interval[0])) {
            overlaps = true;
            break;
          }
        }
      }

      if (!overlaps) {
        available.add(slotStart.toInstant().toString());
      }

      current = current.plusMinutes(stepMinutes);
    }

    return available;
  }

  @Transactional
  public BookResult bookPublicMeeting(String slug, BookMeetingInput input) {
    if (input == null
        || input.slotTime() == null
        || input.name() == null
        || input.name().trim().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_AND_SLOT_REQUIRED");
    }

    String sql = "select * from crm_booking_types where slug = ? and is_active = true";
    var rows = jdbc.query(sql, (rs, rowNum) -> mapBookingType(rs, rowNum), slug);
    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_TYPE_NOT_FOUND");
    BookingTypeDto bt = rows.getFirst();

    Instant slotInstant;
    try {
      slotInstant = Instant.parse(input.slotTime());
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLOT_TIMESTAMP");
    }

    Instant slotEnd = slotInstant.plus(bt.durationMinutes(), ChronoUnit.MINUTES);
    long companyId = Long.parseLong(bt.companyId());
    long hostId = Long.parseLong(bt.hostUserId());

    // Check collision
    List<Long> conflicts =
        jdbc.query(
            """
        select id from crm_meeting_bookings
        where host_user_id = ? and status = 'CONFIRMED'
          and start_time < ? and end_time > ?
        """,
            (rs, rowNum) -> rs.getLong(1),
            hostId,
            Timestamp.from(slotEnd),
            Timestamp.from(slotInstant));

    if (!conflicts.isEmpty()) {
      throw new ApiException(HttpStatus.CONFLICT, "SLOT_ALREADY_BOOKED");
    }

    // Resolve or create contact
    Long contactId =
        resolveOrCreateContact(
            companyId, input.name().trim(), input.email(), input.phone(), hostId);

    long bookingId = IdGenerator.nextId();
    String meetingLink =
        bt.locationDetails() != null && !bt.locationDetails().isEmpty()
            ? bt.locationDetails()
            : "https://meet.google.com/" + generateMeetingCode();

    jdbc.update(
        """
        insert into crm_meeting_bookings (
            id, company_id, booking_type_id, host_user_id, contact_id,
            guest_name, guest_email, guest_phone, notes,
            start_time, end_time, status, meeting_link, created_at, updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', ?, now(), now())
        """,
        bookingId,
        companyId,
        Long.parseLong(bt.id()),
        hostId,
        contactId,
        input.name().trim(),
        input.email() != null ? input.email().trim().toLowerCase() : "",
        input.phone() != null ? input.phone().trim() : "",
        input.notes() != null ? input.notes().trim() : "",
        Timestamp.from(slotInstant),
        Timestamp.from(slotEnd),
        meetingLink);

    // Auto-create CRM task for host
    try {
      workService.createTask(
          companyId,
          "Meeting: " + bt.title() + " with " + input.name().trim(),
          "Scheduled via booking link.\nGuest Email: "
              + input.email()
              + "\nGuest Phone: "
              + input.phone()
              + "\nNotes: "
              + (input.notes() != null ? input.notes() : "None")
              + "\nMeeting Link: "
              + meetingLink,
          "HIGH",
          slotInstant,
          hostId,
          contactId,
          null,
          hostId,
          "OPEN");
    } catch (Exception e) {
      log.warn("Failed to create auto task for meeting booking {}: {}", bookingId, e.getMessage());
    }

    return new BookResult(
        true,
        String.valueOf(bookingId),
        "Meeting confirmed! A calendar invite and link have been prepared.",
        meetingLink,
        slotInstant,
        slotEnd);
  }

  // ================= HELPERS =================

  private Long resolveOrCreateContact(
      long companyId, String name, String email, String phone, long ownerId) {
    if (email != null && !email.trim().isEmpty()) {
      List<Long> found =
          jdbc.query(
              """
          select c.id from contacts c
          join contact_identities i on i.contact_id = c.id
          where c.company_id = ? and i.type = 'EMAIL' and lower(i.value) = lower(?)
          limit 1
          """,
              (rs, rowNum) -> rs.getLong(1),
              companyId,
              email.trim());
      if (!found.isEmpty()) return found.getFirst();
    }

    if (phone != null && !phone.trim().isEmpty()) {
      List<Long> found =
          jdbc.query(
              """
          select c.id from contacts c
          join contact_identities i on i.contact_id = c.id
          where c.company_id = ? and i.type = 'PHONE' and i.value = ?
          limit 1
          """,
              (rs, rowNum) -> rs.getLong(1),
              companyId,
              phone.trim());
      if (!found.isEmpty()) return found.getFirst();
    }

    // Create new contact
    long contactId = IdGenerator.nextId();
    jdbc.update(
        """
        insert into contacts (id, company_id, display_name, status, owner_user_id, created_at, updated_at)
        values (?, ?, ?, 'ACTIVE', ?, now(), now())
        """,
        contactId,
        companyId,
        name,
        ownerId);

    if (email != null && !email.trim().isEmpty()) {
      long identId = IdGenerator.nextId();
      jdbc.update(
          """
          insert into contact_identities (id, company_id, contact_id, type, value, is_primary, created_at, updated_at)
          values (?, ?, ?, 'EMAIL', lower(?), true, now(), now())
          """,
          identId,
          companyId,
          contactId,
          email.trim());
    }

    if (phone != null && !phone.trim().isEmpty()) {
      long identId = IdGenerator.nextId();
      jdbc.update(
          """
          insert into contact_identities (id, company_id, contact_id, type, value, is_primary, created_at, updated_at)
          values (?, ?, ?, 'PHONE', ?, true, now(), now())
          """,
          identId,
          companyId,
          contactId,
          phone.trim());
    }

    return contactId;
  }

  private String generateSlug(long companyId, String requestedSlug, String title) {
    String base =
        requestedSlug != null && !requestedSlug.trim().isEmpty()
            ? sanitizeSlug(requestedSlug)
            : sanitizeSlug(title);
    String current = base;
    int counter = 1;
    while (true) {
      List<Long> existing =
          jdbc.query(
              "select id from crm_booking_types where slug = ?",
              (rs, rowNum) -> rs.getLong(1),
              current);
      if (existing.isEmpty()) return current;
      current = base + "-" + counter;
      counter++;
    }
  }

  private static String sanitizeSlug(String text) {
    if (text == null) return "meeting";
    String slug =
        text.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\-]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    return slug.isEmpty() ? "meeting" : slug;
  }

  private static String generateMeetingCode() {
    String p1 = UUID.randomUUID().toString().substring(0, 3);
    String p2 = UUID.randomUUID().toString().substring(4, 8);
    String p3 = UUID.randomUUID().toString().substring(9, 12);
    return p1 + "-" + p2 + "-" + p3;
  }

  private BookingTypeDto mapBookingType(ResultSet rs, int rowNum) throws SQLException {
    String hostName = null;
    try {
      hostName = rs.getString("host_name");
    } catch (Exception ignored) {
    }

    return new BookingTypeDto(
        String.valueOf(rs.getLong("id")),
        String.valueOf(rs.getLong("company_id")),
        rs.getString("slug"),
        rs.getString("title"),
        rs.getString("description"),
        rs.getInt("duration_minutes"),
        rs.getString("location_type"),
        rs.getString("location_details"),
        String.valueOf(rs.getLong("host_user_id")),
        hostName != null ? hostName : "Sales Rep",
        rs.getBoolean("is_active"),
        rs.getString("working_hours_start"),
        rs.getString("working_hours_end"),
        fromJsonList(rs.getString("days_of_week")),
        rs.getInt("buffer_before_minutes"),
        rs.getInt("buffer_after_minutes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private MeetingBookingDto mapMeetingBooking(ResultSet rs, int rowNum) throws SQLException {
    Long contactIdVal = rs.getObject("contact_id") != null ? rs.getLong("contact_id") : null;
    return new MeetingBookingDto(
        String.valueOf(rs.getLong("id")),
        String.valueOf(rs.getLong("company_id")),
        String.valueOf(rs.getLong("booking_type_id")),
        rs.getString("booking_type_title"),
        String.valueOf(rs.getLong("host_user_id")),
        rs.getString("host_name") != null ? rs.getString("host_name") : "Host",
        contactIdVal != null ? String.valueOf(contactIdVal) : null,
        rs.getString("guest_name"),
        rs.getString("guest_email"),
        rs.getString("guest_phone"),
        rs.getString("notes"),
        rs.getTimestamp("start_time").toInstant(),
        rs.getTimestamp("end_time").toInstant(),
        rs.getString("status"),
        rs.getString("meeting_link"),
        rs.getTimestamp("created_at").toInstant());
  }

  private static String toJson(Object obj) {
    try {
      return MAPPER.writeValueAsString(obj);
    } catch (Exception e) {
      return "[]";
    }
  }

  private static List<String> fromJsonList(String json) {
    if (json == null || json.trim().isEmpty()) return List.of();
    try {
      return MAPPER.readValue(json, STRING_LIST_REF);
    } catch (Exception e) {
      return List.of();
    }
  }
}

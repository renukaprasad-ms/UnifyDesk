package com.messaging.broadcast.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.dto.*;
import com.messaging.broadcast.entity.BroadcastGroup;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.BadRequestException;
import jakarta.mail.internet.InternetAddress;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BroadcastMemberService {

  private final BroadcastGroupService groupService;
  private final BroadcastMemberChunkWriter chunkWriter;
  private final BroadcastProperties properties;
  private final ObjectMapper objectMapper;
  private final JdbcTemplate jdbcTemplate;

  public BroadcastMemberImportResponse add(
      Long companyId, Long groupId, BroadcastMemberBatchRequest request) {
    BroadcastGroup group = groupService.require(companyId, groupId);
    MutableResult result = new MutableResult();
    List<BroadcastMemberChunkWriter.MemberRow> chunk =
        new ArrayList<>(Math.max(1, properties.getMemberWriteBatchSize()));

    for (BroadcastMemberInput input : request.members()) {
      result.processed++;
      BroadcastMemberChunkWriter.MemberRow row = row(group, input);
      if (row == null) {
        result.invalid++;
        continue;
      }
      chunk.add(row);
      if (chunk.size() >= Math.max(1, properties.getMemberWriteBatchSize())) {
        flush(companyId, groupId, chunk, result);
      }
    }
    flush(companyId, groupId, chunk, result);
    return result.response();
  }

  public BroadcastMemberImportResponse importCsv(
      Long companyId, Long groupId, MultipartFile file) {
    BroadcastGroup group = groupService.require(companyId, groupId);
    if (file == null || file.isEmpty()) {
      throw new BadRequestException("CSV file is empty");
    }

    MutableResult result = new MutableResult();
    int batchSize = Math.max(1, properties.getMemberWriteBatchSize());
    List<BroadcastMemberChunkWriter.MemberRow> chunk = new ArrayList<>(batchSize);

    try (Reader reader =
            new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        org.apache.commons.csv.CSVParser parser =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build()
                .parse(reader)) {
      Map<String, Integer> headers = parser.getHeaderMap();
      String destinationHeader =
          headers.keySet().stream()
              .filter(
                  name ->
                      "destination".equalsIgnoreCase(name)
                          || (group.getChannelType() == ChannelType.EMAIL
                              && "email".equalsIgnoreCase(name))
                          || (group.getChannelType() == ChannelType.SMS
                              && ("phone".equalsIgnoreCase(name)
                                  || "mobile".equalsIgnoreCase(name)
                                  || "mobiles".equalsIgnoreCase(name))))
              .findFirst()
              .orElseThrow(
                  () ->
                      new BadRequestException(
                          group.getChannelType() == ChannelType.SMS
                              ? "CSV must contain a phone, mobile or destination header"
                              : "CSV must contain an email or destination header"));
      String nameHeader =
          headers.keySet().stream()
              .filter(name -> "name".equalsIgnoreCase(name) || "displayName".equalsIgnoreCase(name))
              .findFirst()
              .orElse(null);

      for (CSVRecord record : parser) {
        result.processed++;
        String destination = record.get(destinationHeader);
        String displayName = nameHeader == null ? null : record.get(nameHeader);
        Map<String, String> metadata = new LinkedHashMap<>();
        for (String header : headers.keySet()) {
          if (header.equals(destinationHeader) || header.equals(nameHeader)) continue;
          String value = record.isMapped(header) ? record.get(header) : null;
          if (value != null && !value.isBlank()) metadata.put(header, value);
        }
        BroadcastMemberChunkWriter.MemberRow row =
            row(group, new BroadcastMemberInput(destination, displayName, metadata));
        if (row == null) {
          result.invalid++;
          continue;
        }
        chunk.add(row);
        if (chunk.size() >= batchSize) flush(companyId, groupId, chunk, result);
      }
      flush(companyId, groupId, chunk, result);
      return result.response();
    } catch (BadRequestException exception) {
      throw exception;
    } catch (IOException exception) {
      throw new BadRequestException("Unable to read broadcast CSV");
    }
  }

  public void remove(Long companyId, Long groupId, Long memberId) {
    groupService.require(companyId, groupId);
    Instant now = Instant.now();
    int changed =
        jdbcTemplate.update(
            """
            update broadcast_group_members
            set status = 'DISABLED', disabled_at = ?, updated_at = ?
            where id = ? and group_id = ? and company_id = ? and status = 'ACTIVE'
            """,
            java.sql.Timestamp.from(now),
            java.sql.Timestamp.from(now),
            memberId,
            groupId,
            companyId);
    if (changed > 0) {
      jdbcTemplate.update(
          """
          update broadcast_groups
          set member_count = greatest(member_count - 1, 0), updated_at = ?
          where id = ? and company_id = ?
          """,
          java.sql.Timestamp.from(now),
          groupId,
          companyId);
    }
  }

  private BroadcastMemberChunkWriter.MemberRow row(
      BroadcastGroup group, BroadcastMemberInput input) {
    String normalized =
        switch (group.getChannelType()) {
          case EMAIL -> normalizeEmail(input.destination());
          case SMS -> normalizePhone(input.destination());
          case WHATSAPP ->
              throw new BadRequestException(
                  "WhatsApp Broadcast member import is not implemented yet");
        };
    if (normalized == null) return null;
    String displayName =
        input.displayName() == null || input.displayName().isBlank()
            ? null
            : input.displayName().trim();
    String metadataJson = metadata(input.metadata());
    return new BroadcastMemberChunkWriter.MemberRow(
        normalized, normalized, displayName, metadataJson);
  }

  private String normalizeEmail(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      InternetAddress address = new InternetAddress(value.trim(), true);
      address.validate();
      if (address.getAddress() == null || address.getAddress().isBlank()) return null;
      return address.getAddress().trim().toLowerCase(Locale.ROOT);
    } catch (Exception exception) {
      return null;
    }
  }

  private String normalizePhone(String value) {
    if (value == null || value.isBlank()) return null;
    String digits = value.replaceAll("[^0-9]", "");
    if (digits.length() < 8 || digits.length() > 15) return null;
    return digits;
  }

  private String metadata(Map<String, String> metadata) {
    if (metadata == null || metadata.isEmpty()) return null;
    try {
      String value = objectMapper.writeValueAsString(metadata);
      if (value.length() > 8192) {
        throw new BadRequestException("Broadcast member metadata cannot exceed 8 KB");
      }
      return value;
    } catch (JsonProcessingException exception) {
      throw new BadRequestException("Invalid broadcast member metadata");
    }
  }

  private void flush(
      Long companyId,
      Long groupId,
      List<BroadcastMemberChunkWriter.MemberRow> chunk,
      MutableResult result) {
    if (chunk.isEmpty()) return;
    int changed = chunkWriter.write(companyId, groupId, List.copyOf(chunk));
    result.inserted += changed;
    result.duplicates += chunk.size() - changed;
    chunk.clear();
  }

  private static final class MutableResult {
    long processed;
    long inserted;
    long duplicates;
    long invalid;

    BroadcastMemberImportResponse response() {
      return new BroadcastMemberImportResponse(processed, inserted, duplicates, invalid);
    }
  }
}

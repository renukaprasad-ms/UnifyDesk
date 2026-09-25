package com.messaging.emailprovider.domain.dto;

import com.messaging.emailprovider.dns.DnsRecordPurpose;
import com.messaging.emailprovider.dns.DnsRecordType;

public record DnsRecordResponse(
    DnsRecordType type,
    String name,
    String value,
    DnsRecordPurpose purpose,
    boolean required,
    boolean verified) {}

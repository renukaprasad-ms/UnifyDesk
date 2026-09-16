package com.messaging.admin.dto;
import com.messaging.company.enums.CompanyStatus;
public record PlatformCompanyResponse(String id,String name,String slug,String businessEmail,String country,String timezone,CompanyStatus status) {}

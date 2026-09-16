package com.messaging.admin.dto;
public record PlatformUserResponse(String id,String name,String email,String username,boolean verified,boolean twoFactorEnabled) {}

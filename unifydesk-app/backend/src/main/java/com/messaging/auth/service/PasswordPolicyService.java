package com.messaging.auth.service;

import com.messaging.common.exception.BadRequestException;
import org.springframework.stereotype.Service;

@Service
public class PasswordPolicyService {

  public static final int MIN_LENGTH = 12;

  public void validate(String password) {
    if (password == null || password.length() < MIN_LENGTH) {
      throw new BadRequestException("Password must be at least 12 characters");
    }
    if (password.length() > 255) {
      throw new BadRequestException("Password is too long");
    }
  }
}

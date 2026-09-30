package com.messaging.contacts.core.exception;

import com.messaging.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class ContactCoreException extends ApiException {

  public static final String NOT_FOUND = "CONTACT_NOT_FOUND";
  public static final String IDENTITY_CONFLICT = "CONTACT_IDENTITY_CONFLICT";
  public static final String VERSION_CONFLICT = "CONTACT_VERSION_CONFLICT";
  public static final String LIMIT_REACHED = "CONTACT_LIMIT_REACHED";
  public static final String INVALID_IDENTITY = "CONTACT_INVALID_IDENTITY";
  public static final String FIELD_INVALID = "CONTACT_FIELD_INVALID";
  public static final String FIELD_LIMIT_REACHED = "CONTACT_FIELD_LIMIT_REACHED";
  public static final String LIST_LIMIT_REACHED = "CONTACT_LIST_LIMIT_REACHED";
  public static final String PERMISSION_DENIED = "CONTACT_PERMISSION_DENIED";
  public static final String INVALID_CURSOR = "CONTACT_INVALID_CURSOR";
  public static final String INVALID_REQUEST = "CONTACT_INVALID_REQUEST";

  private ContactCoreException(HttpStatus status, String code) {
    super(status, code);
  }

  public static ContactCoreException notFound() {
    return new ContactCoreException(HttpStatus.NOT_FOUND, NOT_FOUND);
  }

  public static ContactCoreException identityConflict() {
    return new ContactCoreException(HttpStatus.CONFLICT, IDENTITY_CONFLICT);
  }

  public static ContactCoreException versionConflict() {
    return new ContactCoreException(HttpStatus.CONFLICT, VERSION_CONFLICT);
  }

  public static ContactCoreException contactLimit() {
    return new ContactCoreException(HttpStatus.FORBIDDEN, LIMIT_REACHED);
  }

  public static ContactCoreException invalidIdentity() {
    return new ContactCoreException(HttpStatus.BAD_REQUEST, INVALID_IDENTITY);
  }

  public static ContactCoreException invalidField() {
    return new ContactCoreException(HttpStatus.BAD_REQUEST, FIELD_INVALID);
  }

  public static ContactCoreException fieldLimit() {
    return new ContactCoreException(HttpStatus.FORBIDDEN, FIELD_LIMIT_REACHED);
  }

  public static ContactCoreException listLimit() {
    return new ContactCoreException(HttpStatus.FORBIDDEN, LIST_LIMIT_REACHED);
  }

  public static ContactCoreException invalidCursor() {
    return new ContactCoreException(HttpStatus.BAD_REQUEST, INVALID_CURSOR);
  }

  public static ContactCoreException invalidRequest() {
    return new ContactCoreException(HttpStatus.BAD_REQUEST, INVALID_REQUEST);
  }
}

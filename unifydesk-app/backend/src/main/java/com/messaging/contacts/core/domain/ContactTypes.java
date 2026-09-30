package com.messaging.contacts.core.domain;

public final class ContactTypes {

  private ContactTypes() {}

  public enum ContactStatus {
    ACTIVE,
    MERGED,
    DELETED
  }

  public enum IdentityType {
    EMAIL,
    PHONE,
    EXTERNAL
  }

  public enum IdentityStatus {
    ACTIVE,
    DISABLED
  }

  public enum CustomFieldType {
    TEXT,
    NUMBER,
    BOOLEAN,
    DATE,
    DATETIME,
    SINGLE_SELECT,
    MULTI_SELECT
  }

  public enum DefinitionStatus {
    ACTIVE,
    ARCHIVED
  }

  public enum ContactListStatus {
    ACTIVE,
    ARCHIVED
  }

  public enum MembershipSource {
    MANUAL,
    IMPORT,
    API
  }

  public enum ContactSort {
    UPDATED_DESC,
    CREATED_DESC,
    NAME_ASC
  }

  public enum CoreField {
    FIRST_NAME,
    LAST_NAME,
    DISPLAY_NAME,
    ORGANIZATION,
    JOB_TITLE,
    COUNTRY_CODE,
    TIMEZONE,
    LANGUAGE,
    SOURCE,
    NOTES
  }
}

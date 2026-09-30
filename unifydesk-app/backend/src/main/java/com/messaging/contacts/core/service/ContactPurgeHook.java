package com.messaging.contacts.core.service;

/**
 * Core-owned deletion integration seam. Lead may wire the existing shared AsyncJob/deletion
 * subsystem without making B1 edit unrelated lifecycle hot files.
 */
public interface ContactPurgeHook {
  void enqueue(long companyId, long contactId, long actorUserId);
}

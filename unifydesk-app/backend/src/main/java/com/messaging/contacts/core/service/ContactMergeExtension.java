package com.messaging.contacts.core.service;

/**
 * Lead/B2 integration seam for current consent/suppression reconciliation during an explicit merge.
 * Implementations must be database-only and participate in the caller's merge transaction.
 */
public interface ContactMergeExtension {

  void reconcile(long companyId, long sourceContactId, long targetContactId, long actorUserId);
}

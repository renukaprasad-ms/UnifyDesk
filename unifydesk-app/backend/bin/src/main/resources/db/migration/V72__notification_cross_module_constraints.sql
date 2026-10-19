-- Notification System Round-1 integration constraints.
-- V70 (core) and V71 (delivery) were intentionally independently migratable.
-- This lead-owned migration adds only constraints whose lifecycle semantics are
-- safe after both worker schemas are present.

alter table notification_delivery_attempts
  add constraint fk_notification_delivery_attempts_intent
  foreign key (intent_id)
  references notification_delivery_intents(id)
  on delete cascade;

alter table notification_delivery_attempts
  add constraint fk_notification_delivery_attempts_subscription
  foreign key (subscription_id)
  references notification_push_subscriptions(id)
  on delete set null;

alter table notification_push_attempts
  add constraint fk_notification_push_attempts_intent
  foreign key (intent_id)
  references notification_delivery_intents(id)
  on delete cascade;

alter table notification_push_attempts
  add constraint fk_notification_push_attempts_subscription
  foreign key (subscription_id)
  references notification_push_subscriptions(id)
  on delete cascade;

alter table notification_digest_items
  add constraint fk_notification_digest_items_intent
  foreign key (delivery_intent_id)
  references notification_delivery_intents(id)
  on delete cascade;

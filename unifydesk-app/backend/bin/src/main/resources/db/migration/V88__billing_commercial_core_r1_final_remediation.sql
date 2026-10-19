-- Billing R1 B1 final remediation: canonical immediate-upgrade invoice source.

alter table invoices
  add column subscription_change_id bigint,
  add column source_reference varchar(160);

alter table invoices
  add constraint fk_invoices_subscription_change
    foreign key (subscription_change_id) references subscription_changes(id);

alter table invoices
  add constraint uk_invoices_subscription_change
    unique (subscription_change_id);

create index idx_invoices_subscription_change
  on invoices (subscription_change_id);

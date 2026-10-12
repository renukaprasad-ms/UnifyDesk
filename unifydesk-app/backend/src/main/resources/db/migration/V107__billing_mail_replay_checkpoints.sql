-- Billing V2 Mail reconciliation: incremental, bounded replay of previously observed
-- source handoffs. The forward cursor is unaffected by replay checkpoint state.
create table billing_mail_replay_checkpoints (
  mail_project_id bigint primary key references billing_mail_project_links(mail_project_id)
    on delete restrict,
  window_from timestamptz,
  window_to timestamptz,
  after_id bigint not null default 0 check (after_id >= 0),
  last_completed_at timestamptz,
  updated_at timestamptz not null default now(),
  constraint ck_mail_replay_window
    check ((window_from is null and window_to is null and after_id = 0)
       or (window_from is not null and window_to is not null
           and window_to > window_from))
);

create index idx_billing_mail_replay_checkpoint_updated
  on billing_mail_replay_checkpoints (updated_at);

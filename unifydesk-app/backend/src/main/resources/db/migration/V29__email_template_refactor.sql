create table email_templates (
  id bigint primary key,
  company_id bigint not null,
  name varchar(160) not null,
  category varchar(32) not null,
  status varchar(24) not null,
  current_version_id bigint,
  published_version_id bigint,
  created_by_user_id bigint not null,
  version bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  archived_at timestamptz,
  constraint fk_email_templates_company foreign key (company_id) references companies(id) on delete cascade,
  constraint fk_email_templates_created_by foreign key (created_by_user_id) references users(id)
);

create table email_template_versions (
  id bigint primary key,
  template_id bigint not null,
  version_number bigint not null,
  editor_mode varchar(16) not null,
  subject_template varchar(998),
  preheader_template varchar(255),
  document_json text,
  html_source text,
  compiled_html text not null,
  compiled_text text not null,
  variables_json text not null default '[]',
  content_hash varchar(64) not null,
  created_by_user_id bigint not null,
  created_at timestamptz not null,
  published_at timestamptz,
  constraint fk_email_template_versions_template foreign key (template_id) references email_templates(id) on delete cascade,
  constraint fk_email_template_versions_created_by foreign key (created_by_user_id) references users(id),
  constraint uk_email_template_versions_template_version unique (template_id, version_number)
);

alter table email_templates
  add constraint fk_email_templates_current_version foreign key (current_version_id) references email_template_versions(id),
  add constraint fk_email_templates_published_version foreign key (published_version_id) references email_template_versions(id);

create unique index uk_email_templates_company_name_active
  on email_templates(company_id, lower(name)) where status <> 'ARCHIVED';
create index idx_email_templates_company_status_updated
  on email_templates(company_id, status, updated_at desc);
create index idx_email_template_versions_template
  on email_template_versions(template_id, version_number desc);

create table email_template_catalog (
  id bigint primary key,
  slug varchar(100) not null unique,
  name varchar(160) not null,
  description varchar(600) not null,
  category varchar(32) not null,
  editor_mode varchar(16) not null,
  subject_template varchar(998),
  preheader_template varchar(255),
  document_json text,
  html_source text,
  active boolean not null default true,
  sort_order integer not null default 0
);

insert into email_templates (
  id, company_id, name, category, status, created_by_user_id, version, created_at, updated_at, archived_at
)
select
  id, company_id, name,
  case category
    when 'MARKETING' then 'PROMOTIONAL'
    when 'TRANSACTIONAL' then 'ANNOUNCEMENT'
    when 'UTILITY' then 'ANNOUNCEMENT'
    else 'MINIMAL'
  end,
  case status when 'ACTIVE' then 'PUBLISHED' when 'ARCHIVED' then 'ARCHIVED' else 'DRAFT' end,
  created_by_user_id, 0, created_at, updated_at, archived_at
from message_templates
where channel_type = 'EMAIL';

insert into email_template_versions (
  id, template_id, version_number, editor_mode, subject_template, preheader_template,
  document_json, html_source, compiled_html, compiled_text, variables_json, content_hash,
  created_by_user_id, created_at, published_at
)
select
  id, id, 1, 'HTML', subject, null, null,
  coalesce(body_html, '<div>' || replace(body_text, E'\n', '<br>') || '</div>'),
  coalesce(body_html, '<div>' || replace(body_text, E'\n', '<br>') || '</div>'),
  body_text, coalesce(variables_json, '[]'),
  md5(coalesce(subject, '') || '|' || body_text || '|' || coalesce(body_html, '')),
  created_by_user_id, created_at, case when status = 'ACTIVE' then updated_at else null end
from message_templates
where channel_type = 'EMAIL';

update email_templates
set current_version_id = id,
    published_version_id = case when status = 'PUBLISHED' then id else null end;

alter table broadcast_messages drop constraint if exists fk_broadcast_messages_source_template;

update broadcast_messages b
set source_template_id = null, source_template_version = null
where source_template_id is not null
  and not exists (select 1 from email_templates e where e.id = b.source_template_id);

update broadcast_messages set source_template_version = 1 where source_template_id is not null;

alter table broadcast_messages
  add constraint fk_broadcast_messages_source_email_template
  foreign key (source_template_id) references email_templates(id) on delete set null;

alter table broadcast_messages add column body_html text;
alter table broadcast_runs add column body_html_snapshot text;

insert into email_template_catalog
(id, slug, name, description, category, editor_mode, subject_template, preheader_template, document_json, active, sort_order)
values
(-101, 'promotional-offer', 'Promotional Offer', 'Hero, offer copy and CTA for sales campaigns.', 'PROMOTIONAL', 'BUILDER',
 'A special offer for {{first_name}}', 'Limited-time savings from our team',
 '{"schemaVersion":1,"settings":{"width":600,"backgroundColor":"#f1f5f9","contentBackgroundColor":"#ffffff","accentColor":"#4f46e5"},"blocks":[{"type":"HEADER","brandText":"YOUR BRAND"},{"type":"HERO","heading":"A special offer just for you","text":"Hi {{first_name}}, save on something you will love."},{"type":"TEXT","text":"Use code {{coupon_code}} before {{expiry_date}}."},{"type":"BUTTON","text":"Shop the offer","url":"{{offer_url}}"},{"type":"FOOTER","text":"Thanks for being part of our community."}]}', true, 10),
(-102, 'product-launch', 'Product Launch', 'Launch a product with a focused value proposition and CTA.', 'PRODUCT_LAUNCH', 'BUILDER',
 'Introducing {{product_name}}', 'Meet what we have been building',
 '{"schemaVersion":1,"settings":{"width":600,"backgroundColor":"#f8fafc","contentBackgroundColor":"#ffffff","accentColor":"#0f172a"},"blocks":[{"type":"HEADER","brandText":"YOUR BRAND"},{"type":"HERO","heading":"Meet {{product_name}}","text":"Built to make your next step simpler."},{"type":"TEXT","text":"Discover the launch details prepared for {{first_name}}."},{"type":"BUTTON","text":"Explore the launch","url":"{{product_url}}"},{"type":"FOOTER","text":"Thanks for following our updates."}]}', true, 20),
(-103, 'newsletter', 'Newsletter', 'Editorial layout for news, highlights and useful links.', 'NEWSLETTER', 'BUILDER',
 '{{company_name}} — monthly update', 'News and product highlights',
 '{"schemaVersion":1,"settings":{"width":640,"backgroundColor":"#f1f5f9","contentBackgroundColor":"#ffffff","accentColor":"#2563eb"},"blocks":[{"type":"HEADER","brandText":"{{company_name}}"},{"type":"HEADING","text":"This month at {{company_name}}"},{"type":"TEXT","text":"Hi {{first_name}}, here are the updates worth knowing."},{"type":"DIVIDER"},{"type":"HEADING","text":"Top story"},{"type":"TEXT","text":"Add your most important update here."},{"type":"BUTTON","text":"Read more","url":"{{newsletter_url}}"},{"type":"FOOTER","text":"Thanks for reading."}]}', true, 30),
(-104, 'welcome', 'Welcome Email', 'Friendly onboarding email with a clear next step.', 'WELCOME', 'BUILDER',
 'Welcome, {{first_name}}', 'Everything you need to get started',
 '{"schemaVersion":1,"settings":{"width":600,"backgroundColor":"#f8fafc","contentBackgroundColor":"#ffffff","accentColor":"#16a34a"},"blocks":[{"type":"HEADER","brandText":"YOUR BRAND"},{"type":"HERO","heading":"Welcome, {{first_name}}!","text":"We are glad you are here."},{"type":"TEXT","text":"Your account is ready. Start by exploring the essentials."},{"type":"BUTTON","text":"Get started","url":"{{getting_started_url}}"},{"type":"FOOTER","text":"Need help? Reply to this email."}]}', true, 40),
(-105, 'event-announcement', 'Event Announcement', 'Promote webinars, launches or meetups with an RSVP CTA.', 'EVENT', 'BUILDER',
 'You are invited: {{event_name}}', 'Save the date and reserve your place',
 '{"schemaVersion":1,"settings":{"width":600,"backgroundColor":"#f1f5f9","contentBackgroundColor":"#ffffff","accentColor":"#7c3aed"},"blocks":[{"type":"HEADER","brandText":"EVENT INVITE"},{"type":"HERO","heading":"{{event_name}}","text":"{{event_date}} · {{event_location}}"},{"type":"TEXT","text":"Hi {{first_name}}, join us for an event worth your time."},{"type":"BUTTON","text":"Reserve my place","url":"{{rsvp_url}}"},{"type":"FOOTER","text":"We hope to see you there."}]}', true, 50),
(-106, 'seasonal-campaign', 'Seasonal Campaign', 'Flexible layout for festival and seasonal promotions.', 'SEASONAL', 'BUILDER',
 '{{campaign_name}} is here', 'A seasonal message from {{company_name}}',
 '{"schemaVersion":1,"settings":{"width":600,"backgroundColor":"#fff7ed","contentBackgroundColor":"#ffffff","accentColor":"#ea580c"},"blocks":[{"type":"HEADER","brandText":"{{company_name}}"},{"type":"HERO","heading":"Celebrate {{campaign_name}} with us","text":"A little something special for {{first_name}}."},{"type":"TEXT","text":"Enjoy {{offer_text}} while the campaign is live."},{"type":"BUTTON","text":"View the campaign","url":"{{offer_url}}"},{"type":"FOOTER","text":"Warm wishes from our team."}]}', true, 60),
(-107, 'announcement', 'Simple Announcement', 'Straightforward template for important updates.', 'ANNOUNCEMENT', 'BUILDER',
 '{{announcement_title}}', 'An important update for you',
 '{"schemaVersion":1,"settings":{"width":600,"backgroundColor":"#f8fafc","contentBackgroundColor":"#ffffff","accentColor":"#0284c7"},"blocks":[{"type":"HEADER","brandText":"YOUR BRAND"},{"type":"HEADING","text":"{{announcement_title}}"},{"type":"TEXT","text":"Hi {{first_name}}, add your important announcement details here."},{"type":"BUTTON","text":"Learn more","url":"{{details_url}}"},{"type":"FOOTER","text":"Thank you for your attention."}]}', true, 70),
(-108, 'minimal-business', 'Minimal Business', 'Lightweight professional email with minimal visual styling.', 'MINIMAL', 'BUILDER',
 '{{subject_line}}', 'A short note from {{company_name}}',
 '{"schemaVersion":1,"settings":{"width":600,"backgroundColor":"#f8fafc","contentBackgroundColor":"#ffffff","accentColor":"#0f172a"},"blocks":[{"type":"HEADER","brandText":"{{company_name}}"},{"type":"TEXT","text":"Hi {{first_name}},\n\nWrite your message here. Keep it clear and useful."},{"type":"BUTTON","text":"Continue","url":"{{action_url}}"},{"type":"FOOTER","text":"{{company_name}}"}]}', true, 80);

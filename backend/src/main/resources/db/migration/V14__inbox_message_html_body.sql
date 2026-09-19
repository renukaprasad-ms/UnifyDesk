alter table inbox_messages
  add column if not exists body_html text;

update inbox_messages
set body_html = body_text
where body_html is null
  and body_text is not null
  and (
    lower(ltrim(body_text)) like '<!doctype html%'
    or lower(ltrim(body_text)) like '<html%'
    or lower(body_text) like '%<body%'
    or lower(body_text) like '%<table%'
  );

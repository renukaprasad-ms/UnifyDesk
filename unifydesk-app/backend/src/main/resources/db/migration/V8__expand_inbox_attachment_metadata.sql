-- Provider attachment identifiers and MIME metadata are opaque external values.
-- Gmail attachment IDs can exceed 255 characters, and one oversized value previously
-- caused the entire sync batch transaction to roll back and retry indefinitely.
--
-- PostgreSQL TEXT is varlena-backed just like VARCHAR and stores only the actual value,
-- so this removes arbitrary provider limits without preallocating additional space.

alter table inbox_message_attachments
  alter column external_attachment_id type text,
  alter column filename type text,
  alter column mime_type type text;

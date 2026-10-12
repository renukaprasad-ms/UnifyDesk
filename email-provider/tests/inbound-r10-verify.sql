\set ON_ERROR_STOP on
-- Read-only assertions for the one generated R10 synthetic delivery key.
-- :delivery_key is safely quoted by psql's :'variable' expansion.
SELECT 1 / CASE WHEN count(*)=1 THEN 1 ELSE 0 END
  AS exactly_one_inbound_job
FROM email_mail_inbound_jobs
WHERE delivery_key=:'delivery_key';

SELECT j.id::text AS job_id,j.status,
       j.mime_size_bytes,j.sha256,
       COALESCE(msg.folder,'NOT_VISIBLE_YET') AS folder
FROM email_mail_inbound_jobs j
LEFT JOIN email_mailbox_messages msg
  ON msg.inbound_job_id=j.id AND msg.mailbox_id=j.mailbox_id
 AND msg.organization_id=j.organization_id
WHERE j.delivery_key=:'delivery_key'
ORDER BY j.id;

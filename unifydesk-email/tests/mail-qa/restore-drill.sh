#!/usr/bin/env bash
# Synthetic local rehearsal only; this does not establish off-node production backups.
set -Eeuo pipefail
[[ -f /.dockerenv && "${MAIL_QA_DISPOSABLE:-}" == YES && "${PGDATABASE:-}" == mail_qa_runtime ]] || exit 2
psql -X -v ON_ERROR_STOP=1 -c 'UPDATE email_mail_domains SET inbound_enabled=FALSE' >/dev/null
work=$(mktemp -d /tmp/mail-restore-XXXXXX)
chmod 0700 "$work"
mkdir "$work/bundle" "$work/restored"
pg_dump --format=custom --no-owner --no-acl > "$work/bundle/database.dump"
cp -a /tmp/mail-objects "$work/bundle/objects"
age-keygen -o "$work/identity" >/dev/null 2>&1
recipient=$(age-keygen -y "$work/identity")
tar -C "$work/bundle" -cf - database.dump objects | age -r "$recipient" -o "$work/backup.tar.age"
age -d -i "$work/identity" "$work/backup.tar.age" | tar -C "$work/restored" -xf -
dropdb --if-exists mail_qa_restore
createdb mail_qa_restore
pg_restore --no-owner --no-acl --dbname=mail_qa_restore "$work/restored/database.dump"
python3 - "$work/restored/objects" <<'PY'
import hashlib,pathlib,subprocess,sys,os
objects=pathlib.Path(sys.argv[1])
query="SELECT mime_object_key,mime_size_bytes,sha256 FROM email_mail_inbound_jobs UNION ALL SELECT mime_object_key,mime_size_bytes,sha256 FROM email_mail_outbound_archives"
def rows(db):
    return subprocess.check_output(['psql','-X','-At','-d',db,'-c',query],text=True).strip().splitlines()
assert sorted(rows(os.environ['PGDATABASE']))==sorted(rows('mail_qa_restore'))
for row in rows('mail_qa_restore'):
    key,size,digest=row.split('|')
    data=(objects/key).read_bytes()
    assert len(data)==int(size) and hashlib.sha256(data).hexdigest()==digest
print('PASS: encrypted snapshot decrypted, restored to separate database, every referenced MIME size/digest verified')
PY

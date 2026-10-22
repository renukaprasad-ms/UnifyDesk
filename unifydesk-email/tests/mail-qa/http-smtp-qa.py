"""Synthetic HTTP and real loopback Postfix integration inside the disposable QA container."""
import hashlib
import base64
import hmac
import json
import os
from pathlib import Path
import re
import smtplib
import subprocess
import time
import urllib.error
import urllib.request
from email.utils import formatdate


def sql(statement):
    return subprocess.check_output(["psql", "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-c", statement], text=True).strip()


tokens = {name: "ude_session_local_disposable_" + name for name in ("owner", "admin", "other")}
sql("""
INSERT INTO email_product_organizations(id,name,contact_email,status,created_at,updated_at)
VALUES(1,'QA','qa@example.test','ACTIVE',now(),now()),(2,'Other','other@other.test','ACTIVE',now(),now());
INSERT INTO email_console_users(id,organization_id,email,display_name,role,status,created_at,updated_at)
VALUES(11,1,'owner@example.test','Owner','OWNER','ACTIVE',now(),now()),
(13,1,'admin@example.test','Admin','ADMIN','ACTIVE',now(),now()),
(21,2,'other@other.test','Other','OWNER','ACTIVE',now(),now());
INSERT INTO email_mail_domains(id,organization_id,domain_name,status,inbound_enabled)
VALUES(31,1,'example.test','VERIFIED',TRUE),(32,2,'other.test','VERIFIED',TRUE);
INSERT INTO email_mailboxes(id,organization_id,domain_id,local_part,status)
VALUES(41,1,31,'owner','ACTIVE'),(42,2,32,'owner','ACTIVE');
INSERT INTO email_mailbox_access(organization_id,mailbox_id,user_id,can_delegate)
VALUES(1,41,11,TRUE),(2,42,21,TRUE);
""")
for ident, user, name in ((101, 11, "owner"), (103, 13, "admin"), (201, 21, "other")):
    digest = hashlib.sha256(tokens[name].encode()).hexdigest()
    sql(f"INSERT INTO email_console_sessions(id,user_id,token_hash,expires_at,created_at,updated_at) VALUES({ident},{user},'{digest}',now()+interval '1 hour',now(),now())")


def request(path, method="GET", body=None, token=None, headers=None):
    combined = dict(headers or {})
    if token:
        combined["Authorization"] = "Bearer " + token
    if isinstance(body, dict):
        body = json.dumps(body).encode()
        combined["Content-Type"] = "application/json"
    req = urllib.request.Request("http://127.0.0.1:8010" + path, data=body, method=method, headers=combined)
    try:
        with urllib.request.urlopen(req, timeout=45) as result:
            return result.status, result.headers, result.read()
    except urllib.error.HTTPError as failure:
        return failure.code, failure.headers, failure.read()


raw = ("From: QA <sender@example.invalid>\r\nTo: owner@example.test\r\n"
       f"Date: {formatdate(usegmt=True)}\r\nMessage-ID: <qa-http-replay@example.invalid>\r\n"
       "Subject: Disposable QA\r\nMIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary=qa\r\n\r\n"
       "--qa\r\nContent-Type: text/plain\r\n\r\nSynthetic body <script>escaped</script>\r\n"
       "--qa\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=report.txt\r\n"
       "Content-Transfer-Encoding: base64\r\n\r\naGVsbG8=\r\n--qa--\r\n").encode()
headers = {"Content-Type": "message/rfc822", "X-UnifyDesk-Envelope-To": "owner@example.test", "X-UnifyDesk-Delivery-Key": "qa-http-replay"}
assert request("/internal/v1/mail/inbound", "POST", raw, headers=headers)[0] == 401
internal = os.environ["EMAIL_PROVIDER_INTERNAL_API_TOKEN"]
first = request("/internal/v1/mail/inbound", "POST", raw, internal, headers)
second = request("/internal/v1/mail/inbound", "POST", raw, internal, headers)
assert first[0] == second[0] == 202, (first, second)
a, b = json.loads(first[2])["data"], json.loads(second[2])["data"]
assert a["id"] == b["id"] and not a["duplicate"] and b["duplicate"]
assert sql("SELECT storage_used_bytes FROM email_mailboxes WHERE id=41") == str(len(raw))


def wait_message(delivery):
    for _ in range(90):
        value = sql(f"SELECT msg.id FROM email_mailbox_messages msg JOIN email_mail_inbound_jobs j ON j.id=msg.inbound_job_id WHERE j.delivery_key LIKE '{delivery}' AND j.status='READY'")
        if value:
            return value
        time.sleep(1)
    raise AssertionError("Scanners did not promote synthetic message: " + sql("SELECT status,scan_error FROM email_mail_inbound_jobs"))


message = wait_message("qa-http-replay")
base = f"/console/v1/mail/mailboxes/41/messages/{message}"
assert request(base + "/preview", token=tokens["owner"])[0] == 200
assert request(base + "/preview", token=tokens["admin"])[0] == 404
assert request(base + "/preview", token=tokens["other"])[0] == 404
assert request("/console/v1/mail/mailboxes/41/activate", "POST", {"userId": "13"}, tokens["admin"])[0] == 404
listing = request(base + "/attachments", token=tokens["owner"])
assert listing[0] == 200, listing
files = json.loads(listing[2])["data"]
assert len(files) == 1 and files[0]["filename"] == "report.txt"
download = request(base + "/attachments/" + str(files[0]["part"]), token=tokens["owner"])
assert download[0] == 200 and download[2] == b"hello", download
assert download[1]["Content-Type"].startswith("application/octet-stream")
assert download[1]["Content-Disposition"].startswith("attachment;")
assert download[1]["X-Content-Type-Options"] == "nosniff"
assert "no-store" in download[1]["Cache-Control"]
grant=request("/console/v1/mail/mailboxes/41/access", "PUT", {"userId":"13","canRead":True,"canSend":False,"canDelegate":False}, tokens["owner"])
assert grant[0] == 200, grant
assert request(base + "/preview", token=tokens["admin"])[0] == 200
assert request("/console/v1/mail/mailboxes/41/access/13", "DELETE", token=tokens["owner"])[0] == 200
assert request(base + "/preview", token=tokens["admin"])[0] == 404
print("PASS: actual HTTP auth, idempotent intake, scan promotion, attachment download, ACL grant/revoke and no admin bypass")

# Independent Python TOTP generation checks the complete Java MFA/login path.
password = "original-disposable-password-123"
salt = os.urandom(16)
hashed = "pbkdf2-sha256$310000$" + base64.b64encode(salt).decode() + "$" + base64.b64encode(hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 310000)).decode()
sql(f"UPDATE email_console_users SET password_hash='{hashed}' WHERE id=11")
sql(f"UPDATE email_console_users SET password_hash='{hashed}' WHERE id=13")
role_result=request('/console/v1/iam/roles','POST',{'name':'QA domain operators','permissions':['MANAGE_DOMAINS'],'password':password,'code':None},tokens['owner'])
assert role_result[0] == 200,role_result
role_id=json.loads(role_result[2])['data']['id']
assigned=request('/console/v1/iam/users/13/role','PUT',{'roleId':role_id,'password':password,'code':None},tokens['owner'])
assert assigned[0] == 200,assigned
admin_credentials={'organizationId':'1','email':'admin@example.test','password':password,'mfaCode':None}
admin_login=request('/console/v1/sessions','POST',admin_credentials)
assert admin_login[0] == 200,admin_login
admin_token=json.loads(admin_login[2])['data']['sessionToken']
assert request('/console/v1/projects','POST',{'name':'Denied project'},admin_token)[0] == 403
assert request('/console/v1/mail/domains','POST',{'domain':'denied.example.test'},admin_token)[0] == 403
assert request(base+'/preview',token=admin_token)[0] == 404
updated=request('/console/v1/iam/roles/'+role_id,'PUT',{'permissions':['MANAGE_PROJECTS'],'password':password,'code':None},tokens['owner'])
assert updated[0] == 200,updated
assert request('/console/v1/me',token=admin_token)[0] == 401
admin_login=request('/console/v1/sessions','POST',admin_credentials)
assert admin_login[0] == 200,admin_login
admin_token=json.loads(admin_login[2])['data']['sessionToken']
project=request('/console/v1/projects','POST',{'name':'QA custom-role project'},admin_token)
assert project[0] == 201,project
print('PASS: scoped custom roles, forbidden capabilities, affected-session revocation and no implicit mailbox access')
enrolled = request("/console/v1/security/mfa/enroll", "POST", {"password":password}, tokens["owner"])
assert enrolled[0] == 200, enrolled
secret = json.loads(enrolled[2])["data"]["secret"]
digest = hmac.new(base64.b32decode(secret), int(time.time() // 30).to_bytes(8, "big"), hashlib.sha1).digest()
offset = digest[-1] & 15
code = str((int.from_bytes(digest[offset:offset+4], "big") & 0x7fffffff) % 1000000).zfill(6)
confirmed = request("/console/v1/security/mfa/confirm", "POST", {"code":code}, tokens["owner"])
assert confirmed[0] == 200, confirmed
recovery = json.loads(confirmed[2])["data"]
credentials = {"organizationId":"1", "email":"owner@example.test", "password":password}
assert request("/console/v1/sessions", "POST", credentials)[0] == 400
credentials["mfaCode"] = recovery[0]
assert request("/console/v1/sessions", "POST", credentials)[0] == 200
assert request("/console/v1/sessions", "POST", credentials)[0] == 400
assert request("/console/v1/security/password", "POST", {"password":password,"newPassword":"changed-disposable-password-123","code":recovery[1]},tokens["owner"])[0] == 200
assert request("/console/v1/me", token=tokens["owner"])[0] == 401
reset_path = "/internal/v1/email-product/organizations/1/users/11/password-reset"
assert request(reset_path,"POST")[0] == 401
reset = request(reset_path,"POST",token=internal)
assert reset[0] == 200, reset
reset_token=json.loads(reset[2])["data"]["token"]
reset_body={"token":reset_token,"newPassword":"recovered-disposable-password-123","code":recovery[2]}
assert request("/console/v1/password-reset","POST",reset_body)[0] == 200
assert request("/console/v1/password-reset","POST",reset_body)[0] == 400
credentials.update(password="recovered-disposable-password-123",mfaCode=recovery[3])
assert request("/console/v1/sessions","POST",credentials)[0] == 200
print("PASS: MFA enrollment, independent TOTP, missing/replayed second factor denial, password change, recovery with MFA retained and session revocation")
assert request('/actuator/prometheus')[0] == 401
metrics=request('/actuator/prometheus',token=internal)
assert metrics[0] == 200 and b'mail_ops_collection_ok' in metrics[2], metrics

# Install only into this disposable image's private Postfix instance.
subprocess.run(["useradd", "--system", "--shell", "/usr/sbin/nologin", "udmail-inbound"], check=True)
private = Path("/etc/unifydesk-mail")
private.mkdir(mode=0o750, exist_ok=True)
subprocess.run(["chown", "root:udmail-inbound", str(private)], check=True)
credential = private / "inbound-curl.conf"
credential.write_text('header = "Authorization: Bearer ' + internal + '"\n')
credential.chmod(0o400)
subprocess.run(["chown", "udmail-inbound", str(credential)], check=True)
(private / "inbound-node-id").write_text("qa\n")
subprocess.run(["install", "-m", "0755", "postfix/inbound-r10/udmail-inbound-pipe.sh", "/usr/local/bin/udmail-inbound-pipe.sh"], check=True)
Path("/usr/local/libexec").mkdir(exist_ok=True)
subprocess.run(["install", "-m", "0755", "postfix/inbound-r10/udmail-inbound-pipe.sh", "/usr/local/libexec/udmail-inbound-pipe.sh"], check=True)
main = Path("postfix/inbound-r10/main.cf.example").read_text().replace("mx1.mail.example.net", "mx.qa.invalid")
Path("/etc/postfix/main.cf").write_text(main + "\ncompatibility_level=3.6\nmaillog_file=/var/log/postfix.log\n")
master = Path("/etc/postfix/master.cf").read_text()
master = re.sub(r"^smtp\s+inet.*$", "127.0.0.1:2526 inet n - n - - smtpd", master, flags=re.M)
Path("/etc/postfix/master.cf").write_text(master + "\n" + Path("postfix/inbound-r10/master.cf.snippet").read_text())
for kind in ("domains", "recipients"):
    template = Path(f"postfix/inbound-r10/pgsql-udmail-{kind}.cf.example").read_text()
    query = template[template.index("query ="):]
    # Retain the exact production recipient/domain query, use only QA credentials.
    config = "hosts = 127.0.0.1\nuser = mail_qa\npassword = local-disposable-qa-only\ndbname = mail_qa_runtime\n" + query
    dest = Path(f"/etc/postfix/pgsql-udmail-{kind}.cf")
    dest.write_text(config)
    dest.chmod(0o640)
    subprocess.run(["chown", "root:postfix", str(dest)], check=True)
subprocess.run(["postfix", "check"], check=True)
subprocess.run(["postfix", "start"], check=True)
for _ in range(30):
    try:
        with smtplib.SMTP("127.0.0.1", 2526, timeout=5) as smtp:
            smtp.ehlo("qa.invalid")
            assert smtp.mail("sender@example.invalid")[0] == 250
            assert smtp.rcpt("owner@example.test")[0] == 250
            assert smtp.rcpt("unknown@example.test")[0] in (550, 554)
            assert smtp.rcpt("recipient@unrelated.invalid")[0] in (550, 554)
            smtp.rset()
            smtp.sendmail("sender@example.invalid", ["owner@example.test"], raw)
        break
    except ConnectionRefusedError:
        time.sleep(1)
else:
    raise AssertionError("Postfix listener did not start")
wait_message("mx-qa-%")
smtp_key=sql("SELECT mime_object_key FROM email_mail_inbound_jobs WHERE delivery_key LIKE 'mx-qa-%'")
smtp_raw=(Path('/tmp/mail-objects')/smtp_key).read_bytes()
assert b'\r\n\r\n' in smtp_raw and smtp_raw.count(b'\n')==smtp_raw.count(b'\r\n'), 'Postfix pipe must preserve RFC 5322 CRLF framing'
assert sql("SELECT count(*) FROM email_mail_inbound_jobs WHERE delivery_key LIKE 'mx-qa-%'") == "1"
print("PASS: real Postfix RCPT allowlisting, unknown-recipient and relay denial, private pipe HTTP 202, durable scan-to-mailbox")

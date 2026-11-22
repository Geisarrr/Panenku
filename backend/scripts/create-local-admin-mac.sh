#!/bin/bash
# Interactive bootstrap for the local Docker database created by start-local-mac.sh.
# The user enters the new credential directly; existing users are never promoted/overwritten.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -t 0 ]]; then echo 'Jalankan sendiri di Terminal interaktif.' >&2; exit 1; fi
echo 'Membuat admin@example.test (Admin Dummy) hanya pada backend-postgres-1/panenku.'
read -r -p 'Ketik BUAT ADMIN DUMMY untuk melanjutkan: ' confirmation
[[ "$confirmation" == 'BUAT ADMIN DUMMY' ]] || exit 1
read -r -s -p 'Kata sandi dummy baru (12-72 byte): ' local_admin_password
echo
if [[ ${#local_admin_password} -lt 12 || ${#local_admin_password} -gt 72 ]]; then
  echo 'Gunakan 12-72 karakter ASCII untuk kata sandi dummy.' >&2; exit 1
fi
read -r -s -p 'Ulangi kata sandi: ' repeated_password
echo
[[ "$local_admin_password" == "$repeated_password" ]] || { echo 'Kata sandi berbeda.' >&2; exit 1; }
admin_hash="$(printf '%s\n' "$local_admin_password" | /usr/sbin/htpasswd -niBC 10 dummy)"
admin_hash="${admin_hash#dummy:}"
unset local_admin_password repeated_password
[[ "$admin_hash" == '$2'* ]] || { echo 'Pembuatan hash gagal.' >&2; exit 1; }
docker exec -i backend-postgres-1 sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d panenku "$@"' sh \
  -v "admin_hash=$admin_hash" <<'SQL'
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('panenku-local-admin-bootstrap'));
INSERT INTO users(id,email,password_hash,role,status,display_name)
SELECT gen_random_uuid(),'admin@example.test',:'admin_hash','admin','active','Admin Dummy'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE role='admin')
ON CONFLICT (email) DO NOTHING;
SELECT email,role,display_name FROM users WHERE email='admin@example.test';
COMMIT;
SQL
unset admin_hash
echo 'Jika tabel menampilkan admin@example.test dengan role admin, akun siap dipakai. Akun lama tidak diubah.'

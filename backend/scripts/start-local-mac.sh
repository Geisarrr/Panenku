#!/bin/bash
# Local development only. Run from any directory with: bash scripts/start-local-mac.sh
set -euo pipefail
backend_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$backend_dir"
if [[ ! -f .env ]]; then
  echo 'File backend/.env belum tersedia. Siapkan DB_PASSWORD dan JWT_SECRET dahulu.' >&2
  exit 1
fi
set -a
source .env
set +a
if ! java17_home=$(/usr/libexec/java_home -v 17 2>/dev/null); then
  java17_home=''
  for candidate in "$HOME"/.gradle/jdks/eclipse_adoptium-17-*/jdk-*/Contents/Home; do
    if [[ -x "$candidate/bin/java" ]]; then java17_home="$candidate"; break; fi
  done
fi
if [[ -z "$java17_home" ]]; then echo 'JDK 17 belum ditemukan.' >&2; exit 1; fi
export JAVA_HOME="$java17_home"
export DB_URL='jdbc:postgresql://localhost:5432/panenku'
export PRODUCT_PHOTOS_ENABLED=true
export PRODUCT_PHOTOS_LOCAL_DIR="$HOME/Library/Application Support/Panenku/product-photos"
export PRODUCT_PHOTOS_LOCAL_BASE_URL='http://127.0.0.1:8080'
export KTP_DUMMY_ENABLED=true
# Optional local-only business-time shift for repeatable UAT; default remains real time.
export PANENKU_DEV_CLOCK_OFFSET="${PANENKU_DEV_CLOCK_OFFSET:-PT0S}"
export PAYMENT_STORAGE_LOCAL_DIR="$HOME/Library/Application Support/Panenku/private"
mkdir -p "$PAYMENT_STORAGE_LOCAL_DIR"
chmod 700 "$PAYMENT_STORAGE_LOCAL_DIR"
docker compose up -d postgres
echo 'Menjalankan backend lokal port 8080; tekan Ctrl+C untuk berhenti.'
exec bash ./gradlew bootRun

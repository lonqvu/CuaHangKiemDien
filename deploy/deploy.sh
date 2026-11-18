#!/usr/bin/env bash
# Cap nhat ban moi tren VPS - GitHub Actions goi qua SSH sau khi test dat (cung chay tay duoc):
#   bash deploy/deploy.sh [commit]        # khong ghi commit = ban moi nhat cua main
#
#  1. Sao luu database truoc (backups/truoc-deploy-*.dump)
#  2. Lay dung commit da test tren main
#  3. Build + chay lai (docker compose -f docker-compose.vps.yml up -d --build)
#  4. Kiem tra app song (frontend -> backend tra loi) trong toi da 3 phut
#  5. Khong song -> tu quay ve commit truoc + build lai, bao loi (database da sao luu o buoc 1 neu can khoi phuc)
set -euo pipefail

# Toan bo nam trong ham main: bash doc HET script truoc khi chay -> git checkout doi chinh file nay giua chung khong anh huong
main() {
cd "$(dirname "$0")/.."
TARGET="${1:-}"
COMPOSE=(docker compose -f docker-compose.vps.yml)
# chi dung khi kiem thu script: them file compose phu (vd image dung san)
if [ -n "${DEPLOY_COMPOSE_EXTRA:-}" ]; then COMPOSE+=(-f "$DEPLOY_COMPOSE_EXTRA"); fi
HEALTH_TIMEOUT="${DEPLOY_HEALTH_TIMEOUT:-180}"

log() { echo "[deploy $(date '+%F %T')] $*"; }

[ -f .env ] || { log "THIEU file .env (xem docs/trien-khai-vps.md muc 3)"; exit 1; }

PREV="$(git rev-parse HEAD)"
log "dang chay: ${PREV:0:7}"

# ---- 1. sao luu truoc khi doi ----
mkdir -p backups
if "${COMPOSE[@]}" ps --status running --services 2>/dev/null | grep -qx db; then
  f="backups/truoc-deploy-$(date +%Y%m%d-%H%M%S)-${PREV:0:7}.dump"
  "${COMPOSE[@]}" exec -T db pg_dump -U shop -d shop -Fc > "$f.tmp" && mv "$f.tmp" "$f"
  log "da sao luu: $f"
  ls -1t backups/truoc-deploy-*.dump 2>/dev/null | tail -n +11 | xargs -r rm -f -- # giu 10 ban gan nhat
else
  log "database chua chay (lan dau) - bo qua sao luu"
fi

# ---- 2. lay code ----
git fetch --quiet origin main
if [ -n "$TARGET" ]; then
  git merge-base --is-ancestor "$TARGET" origin/main || { log "commit $TARGET khong nam tren main - dung lai"; exit 1; }
else
  TARGET="$(git rev-parse origin/main)"
fi
git checkout --quiet --force -B main "$TARGET"
log "chuyen sang: ${TARGET:0:7} ($(git log -1 --format=%s | cut -c1-80))"

up() { "${COMPOSE[@]}" up -d --build --remove-orphans; }

# app song = qua frontend (nginx) goi duoc backend: /api/auth/2fa chua dang nhap tra 401 (khong phai 502/504)
healthy() {
  local end=$((SECONDS + HEALTH_TIMEOUT))
  while [ $SECONDS -lt $end ]; do
    # wget coi 401 la loi (exit 1) -> lay chu ra truoc roi moi tim, khong de pipefail lam hong ket qua
    local out
    out="$("${COMPOSE[@]}" exec -T frontend wget -S -q -O /dev/null http://127.0.0.1/api/auth/2fa 2>&1 || true)"
    if grep -q " 401" <<< "$out"; then return 0; fi
    sleep 5
  done
  return 1
}

# ---- 3 + 4. build, chay, kiem tra ----
if up && healthy; then
  log "OK: ${TARGET:0:7} dang chay"
  docker image prune -f > /dev/null || true
  exit 0
fi

# ---- 5. quay ve ban truoc ----
log "LOI: ban moi khong len trong ${HEALTH_TIMEOUT}s -> quay ve ${PREV:0:7}"
"${COMPOSE[@]}" logs --tail 80 backend || true
git checkout --quiet --force -B main "$PREV"
if up && healthy; then
  log "da quay ve ${PREV:0:7}, app chay lai binh thuong. Ban ${TARGET:0:7} KHONG duoc deploy."
else
  log "QUAY VE CUNG LOI - can xu ly tay (docs/trien-khai-vps.md muc 8). Ban sao luu truoc deploy nam trong backups/"
fi
exit 1
}

main "$@"
exit $?

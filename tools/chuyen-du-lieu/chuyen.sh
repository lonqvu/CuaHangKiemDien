#!/usr/bin/env bash
# Chuyen du lieu phan mem cu (BackupFile.bak, SQL Server) sang he thong moi (PostgreSQL). Chi can Docker.
# Huong dan day du: docs/chuyen-du-lieu-cu.md
#
#   bash tools/chuyen-du-lieu/chuyen.sh xuat BackupFile.bak        # .bak -> du-lieu-cu.json (can ~2 GB RAM trong)
#   bash tools/chuyen-du-lieu/chuyen.sh nap  du-lieu-cu.json        # tren VPS: sao luu DB -> nap -> doi chieu
#   bash tools/chuyen-du-lieu/chuyen.sh tat-ca BackupFile.bak      # ca 2 buoc tren cung may
#
# Mac dinh nap vao database cua docker-compose.vps.yml (doc DB_PASSWORD trong .env).
# Nap vao database khac: dat PG_URL=postgres://user:pass@host:5432/db (chay voi --network host).
set -euo pipefail

TOOL="$(cd "$(dirname "$0")" && pwd)"
APP="$(cd "$TOOL/../.." && pwd)"
COMPOSE=(docker compose -f "$APP/docker-compose.vps.yml")
NODE_IMG=node:22-alpine
MSSQL_IMG=mcr.microsoft.com/mssql/server:2022-latest
log() { echo "[$(date '+%H:%M:%S')] $*"; }

# chay 1 lenh node trong container (khong can cai Node tren may), thu muc lam viec = $2
node_run() {
  local net="$1" work="$2"; shift 2
  docker run --rm --network "$net" -v "$TOOL:/tool:ro" -v "$work:/work" -w /work \
    -e MSSQL_URL="${MSSQL_URL:-}" -e PG_URL="${PG_URL:-}" "$NODE_IMG" \
    sh -c "cp -r /tool /app && cd /app && { [ -d node_modules ] || npm ci --omit=dev --no-audit --no-fund --loglevel=error; } && cd /work && $*"
}

xuat() {
  local bak; bak="$(realpath "$1")"
  [ -f "$bak" ] || { echo "Khong thay file $1"; exit 1; }
  local out; out="$(dirname "$bak")"
  local tmp; tmp="$(mktemp -d)"; chmod 777 "$tmp"; cp "$bak" "$tmp/old.bak"; chmod 644 "$tmp/old.bak"
  local net=chuyen-du-lieu-tam pass="Tam#$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')"
  docker network create "$net" >/dev/null 2>&1 || true
  docker rm -f chuyen-mssql >/dev/null 2>&1 || true
  don() { docker rm -f chuyen-mssql >/dev/null 2>&1 || true; docker network rm "$net" >/dev/null 2>&1 || true; rm -rf "$tmp"; }
  trap don EXIT

  log "Khoi dong SQL Server tam (lan dau tai image ~1.5 GB)..."
  docker run -d --name chuyen-mssql --network "$net" -e ACCEPT_EULA=Y -e "MSSQL_SA_PASSWORD=$pass" -v "$tmp:/bak" "$MSSQL_IMG" >/dev/null
  local sq=(docker exec chuyen-mssql /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$pass" -b)
  for _ in $(seq 60); do "${sq[@]}" -Q "SELECT 1" >/dev/null 2>&1 && break; sleep 3; done
  log "Khoi phuc $1..."
  "${sq[@]}" -Q "RESTORE DATABASE NhomSatKiemDien FROM DISK='/bak/old.bak' WITH MOVE 'NhomSatKiemDien' TO '/var/opt/mssql/data/n.mdf', MOVE 'NhomSatKiemDien_log' TO '/var/opt/mssql/data/n.ldf'" >/dev/null
  log "Xuat du lieu..."
  MSSQL_URL="Server=chuyen-mssql,1433;Database=NhomSatKiemDien;User Id=sa;Password=$pass;Encrypt=false;TrustServerCertificate=true" \
    node_run "$net" "$out" node /app/xuat.cjs du-lieu-cu.json
  don; trap - EXIT # tat SQL Server tam ngay (tra RAM) truoc buoc nap
  log "Xong: $out/du-lieu-cu.json"
}

nap() {
  local json; json="$(realpath "$1")"
  [ -f "$json" ] || { echo "Khong thay file $1"; exit 1; }
  local work; work="$(dirname "$json")"
  local net=host
  if [ -z "${PG_URL:-}" ]; then
    cd "$APP"
    local pw; pw="$(grep -E '^DB_PASSWORD=' .env | head -1 | cut -d= -f2-)"
    local db; db="$("${COMPOSE[@]}" ps -q db)"
    [ -n "$db" ] || { echo "Database chua chay: docker compose -f docker-compose.vps.yml up -d"; exit 1; }
    net="$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$db")"
    PG_URL="postgres://shop:$(node_enc "$pw")@db:5432/shop"
    # sao luu truoc (du database moi): loi giua chung thi van con ban nay
    mkdir -p backups
    local f="backups/truoc-chuyen-du-lieu-$(date +%Y%m%d-%H%M%S).dump"
    "${COMPOSE[@]}" exec -T db pg_dump -U shop -d shop -Fc > "$f"
    log "Da sao luu database hien tai: $f"
  fi
  log "Nap du lieu..."
  PG_URL="$PG_URL" node_run "$net" "$work" node /app/nap.cjs "$(basename "$json")"
  log "Doi chieu tung dong giua du lieu cu va moi..."
  local rpt; rpt="bao-cao-chuyen-du-lieu-$(date +%Y%m%d-%H%M).txt"
  if PG_URL="$PG_URL" node_run "$net" "$work" node /app/kiem-tra.cjs "$(basename "$json")" "$rpt"; then
    log "KHOP HET. Bao cao: $work/$rpt"
  else
    log "CO CHO LECH - xem $work/$rpt (database da sao luu truoc khi nap)"; exit 1
  fi
}

# ma hoa mat khau cho URL (ky tu dac biet)
node_enc() { python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$1" 2>/dev/null || printf '%s' "$1"; }

case "${1:-}" in
  xuat) xuat "${2:?Thieu file .bak}" ;;
  nap) nap "${2:?Thieu file du-lieu-cu.json}" ;;
  tat-ca) xuat "${2:?Thieu file .bak}"; nap "$(dirname "$(realpath "$2")")/du-lieu-cu.json" ;;
  *) sed -n '2,10p' "$0"; exit 1 ;;
esac

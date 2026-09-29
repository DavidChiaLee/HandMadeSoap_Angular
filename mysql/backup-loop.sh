#!/bin/sh
# handsoap auto-backup loop: dumps the handsoap DB and essential-oil images.
# The SQL dump contains live data, including admins.password_hash and the
# contacts table (customer name / email / phone). Treat it as SECRET:
# BACKUP_DIR must point at a directory excluded by .gitignore (./mysql/backup),
# never at ./mysql/init, because that directory is mounted as
# docker-entrypoint-initdb.d and would otherwise commit credentials into git.
# Applying the dump on a fresh volume is done by mysql/init/05-restore-seed.sh.
# The image archive contains the files referenced by essential_oil_images.image_path.
set -u

HOST="${MYSQL_HOST:-mysql}"
PASS="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
INTERVAL="${BACKUP_INTERVAL:-300}"
RETRY="${BACKUP_RETRY:-10}"
OUT_DIR="${BACKUP_DIR:-/backup}"
DB="${BACKUP_DB:-handsoap}"
OUT="$OUT_DIR/05-latest-backup.sql"
TMP="$OUT_DIR/.05-tmp.sql"
ERR="$OUT_DIR/.last-err"
UPLOADS_DIR="${UPLOADS_DIR:-/uploads}"
UPLOADS_OUT="${UPLOADS_BACKUP:-/backups/essential-oil-uploads-latest.tar.gz}"
UPLOADS_TMP="$UPLOADS_OUT.tmp"
UPLOADS_LIST="$OUT_DIR/.essential-oil-images.list"

export MYSQL_PWD="$PASS"

log() { echo "[backup] $(date '+%Y-%m-%d %H:%M:%S') $*"; }

dump_once() {
  mysqldump -h "$HOST" -uroot \
    --default-character-set=utf8mb4 \
    --single-transaction \
    --no-create-info \
    --insert-ignore \
    --complete-insert \
    --skip-extended-insert \
    --skip-add-locks \
    --skip-disable-keys \
    "$DB" > "$TMP"
}

backup_uploads() {
  if ! mysql -h "$HOST" -uroot --batch --skip-column-names "$DB" \
    -e "SELECT image_path FROM essential_oil_images ORDER BY id;" > "$UPLOADS_LIST"; then
    log "essential oil image list failed"
    return 1
  fi

  if [ ! -s "$UPLOADS_LIST" ]; then
    rm -f "$UPLOADS_TMP" "$UPLOADS_LIST"
    log "essential oil image backup skipped (no image records)"
    return 0
  fi

  while IFS= read -r image; do
    case "$image" in
      ""|/*|*/*)
        log "invalid essential oil image path"
        return 1
        ;;
    esac
    if [ ! -f "$UPLOADS_DIR/$image" ]; then
      log "missing essential oil image file: $image"
      return 1
    fi
  done < "$UPLOADS_LIST"

  if ! tar -czf "$UPLOADS_TMP" -C "$UPLOADS_DIR" -T "$UPLOADS_LIST"; then
    log "essential oil image archive failed"
    return 1
  fi

  mv "$UPLOADS_TMP" "$UPLOADS_OUT"
  log "essential oil image backup ok ($(wc -l < "$UPLOADS_LIST" | tr -d ' ') images, $(wc -c < "$UPLOADS_OUT" | tr -d ' ') bytes)"
  rm -f "$UPLOADS_LIST"
}

backup_once() {
  dump_once || return 1
  backup_uploads || { rm -f "$TMP" "$UPLOADS_TMP"; return 1; }
  mv "$TMP" "$OUT"
  log "database backup ok ($(wc -c < "$OUT" | tr -d ' ') bytes)"
}

log "starting; host=$HOST db=$DB interval=${INTERVAL}s retry=${RETRY}s target=$OUT images=$UPLOADS_OUT"

while true; do
  if backup_once 2>"$ERR"; then
    rm -f "$ERR"
    sleep "$INTERVAL"
  else
    log "backup FAILED: $(cat "$ERR" 2>/dev/null)"
    rm -f "$TMP" "$UPLOADS_TMP"
    sleep "$RETRY"
  fi
done

#!/bin/sh
# handsoap auto-backup loop: dumps the handsoap DB and ALL files under uploads/.
# The SQL dump contains live data, including admins.password_hash and the
# contacts table (customer name / email / phone). Treat it as SECRET:
# BACKUP_DIR must point at a directory excluded by .gitignore (./mysql/backup),
# never at ./mysql/init, because that directory is mounted as
# docker-entrypoint-initdb.d and would otherwise commit credentials into git.
# Applying the dump on a fresh volume is done by mysql/init/05-restore-seed.sh.
#
# ---- 產出（review H7）----
#   mysql/backup/handsoap-<YYYYmmdd-HHMM>.sql.gz          資料庫（gzip）
#   backups/handsoap-uploads-<YYYYmmdd-HHMM>.tar.gz       uploads/ 整目錄封存
#   backups/checksums/<YYYYmmdd-HHMM>.sha256              兩者的校驗值
#   mysql/backup/05-latest-backup.sql                     相容別名（明文，供自動還原）
#
# ---- 三個關鍵設計（不要「順手簡化」，每個都有實測或事故背景）----
#
# ① 檔名用 %Y%m%d-%H%M 而非原建議的 %F-%h。
#    %F-%h 是「每小時一檔」，但本腳本 INTERVAL=300（5 分鐘）= 每日 288 次，
#    同一小時內會有 12 份 dump 寫進同一個檔名互相覆寫 —— 等於沒有保留策略。
#
# ② 圖片採「整目錄 tar」，DB 的 union 三表只用來「預警缺檔」，不決定備份內容。
#    舊版只備 essential_oil_images 等於漏掉 72% 的圖（實測 82 張中只備 33 張）。
#    M2（刪圖不刪磁碟檔）尚未修，orphan 只會累積；union 會讓 orphan 永遠消失在
#    備份外，災後一樣還不出來。故整目錄才是完整還原的正確範圍。
#
# ③ union 缺檔「只警告、不中斷」。舊版缺一張圖就 return 1，導致整次備份失敗 ——
#    擴大到三表後，等於一張圖不見就讓 DB 備份也連續停擺，風險遠大於原問題。
set -u

HOST="${MYSQL_HOST:-mysql}"
PASS="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
INTERVAL="${BACKUP_INTERVAL:-300}"
RETRY="${BACKUP_RETRY:-10}"
OUT_DIR="${BACKUP_DIR:-/backup}"
DB="${BACKUP_DB:-handsoap}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-7}"
UPLOADS_DIR="${UPLOADS_DIR:-/uploads}"
UPLOADS_OUT_DIR="${UPLOADS_BACKUP_DIR:-/backups}"
CHECKSUM_DIR="${CHECKSUM_DIR:-/checksums}"

# 相容別名：保留明文的 05-latest-backup.sql，讓 mysql/init/05-restore-seed.sh
# 與既有文件（README 還原指令）零改動。
LEGACY_OUT="$OUT_DIR/05-latest-backup.sql"

# 每次執行產生一個時間戳，所有產物共用，確保同一輪的檔名一致。
STAMP="$(date '+%Y%m%d-%H%M')"
OUT="$OUT_DIR/handsoap-$STAMP.sql.gz"
TMP="$OUT_DIR/.handsoap-$STAMP.sql.gz.tmp"
ERR="$OUT_DIR/.last-err"
UPLOADS_OUT="$UPLOADS_OUT_DIR/handsoap-uploads-$STAMP.tar.gz"
UPLOADS_TMP="$UPLOADS_OUT.tmp"
REF_LIST="$OUT_DIR/.image-refs.list"

export MYSQL_PWD="$PASS"

log() { echo "[backup] $(date '+%Y-%m-%d %H:%M:%S') $*"; }

# mysqldump 被中斷（例如容器被 kill）會留下半截 SQL，而 shell 的 > 轉向
# 仍回傳 exit 0 —— 舊版因此會把半截 dump 當成成功。mysqldump 正常結束時
# 最後一行固定為 "-- Dump completed on ..."，以此判定完整性。
dump_is_complete() {
  [ -s "$1" ] || return 1
  gzip -t "$1" 2>/dev/null || return 1
  gzip -dc "$1" 2>/dev/null | tail -1 | grep -q '^-- Dump completed on '
}

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
    "$DB" | gzip > "$TMP"
}

# 整目錄封存：-C "$UPLOADS_DIR" 後用 "." 代表目錄內容本身，
# 讓解開後不會多出一層 uploads/ 目錄。
backup_uploads() {
  if ! tar -czf "$UPLOADS_TMP" -C "$UPLOADS_DIR" . ; then
    log "uploads archive failed"
    return 1
  fi
  if [ ! -s "$UPLOADS_TMP" ]; then
    log "uploads archive is empty"
    rm -f "$UPLOADS_TMP"
    return 1
  fi
  mv "$UPLOADS_TMP" "$UPLOADS_OUT"
  log "uploads backup ok ($(wc -c < "$UPLOADS_OUT" | tr -d ' ') bytes)"
}

# 列出 DB 三張 image 表參照的所有檔名（UNION 自動去重）。
# 僅供 warn_missing_files 使用，不決定備份內容（見檔頭 ②）。
collect_image_refs() {
  mysql -h "$HOST" -uroot --batch --skip-column-names "$DB" -e "
    SELECT image_path FROM soap_images
    UNION
    SELECT image_path FROM oil_images
    UNION
    SELECT image_path FROM essential_oil_images;
  " 2>/dev/null > "$REF_LIST"
}

# 只警告、不阻斷備份（見檔頭 ③）。缺檔代表 M2 的殘留或磁碟異常，
# 但此時 DB dump 與圖片封包都已完成，不該因為它們被丟掉。
warn_missing_files() {
  if ! collect_image_refs; then
    log "warning: 無法讀取 image_path 清單（跳過缺檔檢查）"
    return 0
  fi
  missing=0
  while IFS= read -r image; do
    [ -n "$image" ] || continue
    # 防路徑穿越：image_path 來自 DB，不應含斜線或開頭斜線
    case "$image" in
      /*|*/*)
        log "warning: 異常 image_path（已跳過）：$image"
        continue
        ;;
    esac
    if [ ! -f "$UPLOADS_DIR/$image" ]; then
      log "warning: DB 有紀錄但磁碟缺檔：$image"
      missing=$((missing + 1))
    fi
  done < "$REF_LIST"

  if [ "$missing" -gt 0 ]; then
    log "warning: 共 $missing 個圖片缺失（備份仍已完成，請檢查 uploads/ 磁碟）"
  else
    log "image check ok: DB 參照的圖片全部存在"
  fi
  rm -f "$REF_LIST"
  return 0
}

write_checksums() {
  [ -n "$CHECKSUM_DIR" ] || return 0
  mkdir -p "$CHECKSUM_DIR" 2>/dev/null || {
    log "warning: 無法建立 $CHECKSUM_DIR，略過校驗值"
    return 0
  }
  # 校驗值寫在獨立目錄而非與備份同目錄：同一顆磁碟同時壞掉時，
  # 放在一起等於沒有校驗。獨立目錄可單獨 rsync 到異機。
  if (cd "$OUT_DIR" && sha256sum "$(basename "$OUT")") > "$CHECKSUM_DIR/$STAMP.sha256" 2>/dev/null; then
    log "checksum written: $CHECKSUM_DIR/$STAMP.sha256"
  else
    log "warning: sha256sum 失敗（備份本身不受影響）"
  fi
}

# 保留策略：只刪自己產出的 handsoap-* 檔案，絕不碰 .gitkeep 與相容別名。
# -mtime +N = 最後修改時間超過 N 天（find 的 +N 是「嚴格大於」）。
# -maxdepth 1 確保不會遞迴進子目錄；[ -d ] 防護避免傳入空字串時掃到根目錄。
purge_old() {
  [ -n "$1" ] && [ -d "$1" ] || return 0
  find "$1" -maxdepth 1 -type f \
    -name 'handsoap-*.sql.gz' \
    -mtime "+$KEEP_DAYS" -print -delete 2>/dev/null
}

purge_old_uploads() {
  [ -n "$UPLOADS_OUT_DIR" ] && [ -d "$UPLOADS_OUT_DIR" ] || return 0
  find "$UPLOADS_OUT_DIR" -maxdepth 1 -type f \
    -name 'handsoap-uploads-*.tar.gz' \
    -mtime "+$KEEP_DAYS" -print -delete 2>/dev/null
}

purge_old_checksums() {
  [ -n "$CHECKSUM_DIR" ] && [ -d "$CHECKSUM_DIR" ] || return 0
  find "$CHECKSUM_DIR" -maxdepth 1 -type f \
    -name '*.sha256' \
    -mtime "+$KEEP_DAYS" -print -delete 2>/dev/null
}

backup_once() {
  dump_once || return 1
  if ! dump_is_complete "$TMP"; then
    log "dump 不完整或損毀，放棄本輪（不覆寫既有備份）"
    rm -f "$TMP"
    return 1
  fi
  mv "$TMP" "$OUT"
  log "database backup ok ($(wc -c < "$OUT" | tr -d ' ') bytes)"

  backup_uploads || return 1
  write_checksums

  # 相容別名：解壓成明文供 05-restore-seed.sh 使用。
  # 寫入前先解到暫存再 mv，避免還原程序讀到寫到一半的檔案。
  if gzip -dc "$OUT" > "$LEGACY_OUT.tmp" 2>/dev/null && [ -s "$LEGACY_OUT.tmp" ]; then
    mv "$LEGACY_OUT.tmp" "$LEGACY_OUT"
  else
    rm -f "$LEGACY_OUT.tmp"
    log "warning: 無法產生相容別名 $LEGACY_OUT"
  fi

  warn_missing_files
  purge_old "$OUT_DIR"
  purge_old_uploads
  purge_old_checksums
  log "backup completed (stamp=$STAMP keep_days=$KEEP_DAYS)"
}

log "starting; host=$HOST db=$DB interval=${INTERVAL}s retry=${RETRY}s keep_days=${KEEP_DAYS}d"
log "sql -> $OUT"
log "uploads -> $UPLOADS_OUT"
log "checksums -> $CHECKSUM_DIR"

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

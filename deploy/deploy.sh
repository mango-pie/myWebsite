#!/usr/bin/env bash
#
# 一键升级脚本（在服务器上执行）：备份 -> 替换 JAR -> 启动(自动 Flyway 迁移) -> 健康检查 -> 失败自动回滚
#
# 用法：
#   sudo bash deploy/deploy.sh /path/to/AI-0.0.1-SNAPSHOT.jar
#
# 前置：
#   - /etc/ai-backend.env 已配置 DB_USER / DB_PASSWORD 等（用于数据库备份）
#   - systemd 服务名为 ai-backend，应用目录 /opt/ai-backend
#
set -euo pipefail

NEW_JAR="${1:-}"
APP_ROOT="${APP_ROOT:-/opt/ai-backend}"
JAR_NAME="${JAR_NAME:-AI-0.0.1-SNAPSHOT.jar}"
SERVICE="${SERVICE:-ai-backend}"
ENV_FILE="${ENV_FILE:-/etc/ai-backend.env}"
DB_NAME="${DB_NAME:-aiscene}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8123/api/actuator/health}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_INTERVAL="${HEALTH_INTERVAL:-3}"

BACKUP_DIR="${BACKUP_DIR:-$APP_ROOT/backups}"
STAMP="$(date +%F_%H%M%S)"

log() { echo -e "\033[1;34m[deploy]\033[0m $*"; }
err() { echo -e "\033[1;31m[deploy]\033[0m $*" >&2; }

if [[ -z "$NEW_JAR" || ! -f "$NEW_JAR" ]]; then
    err "请提供新 JAR 路径：sudo bash deploy/deploy.sh /path/to/app.jar"
    exit 1
fi

mkdir -p "$BACKUP_DIR"

# 读取 DB 凭据（仅用于备份）
if [[ -f "$ENV_FILE" ]]; then
    # shellcheck disable=SC1090
    set -a; source "$ENV_FILE"; set +a
fi
DB_USER="${DB_USER:-aiscene}"

# 1. 备份数据库
if command -v mysqldump >/dev/null 2>&1 && [[ -n "${DB_PASSWORD:-}" ]]; then
    DB_BACKUP="$BACKUP_DIR/db_${DB_NAME}_${STAMP}.sql"
    log "备份数据库 -> $DB_BACKUP"
    mysqldump -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" > "$DB_BACKUP"
else
    err "跳过数据库备份（未找到 mysqldump 或未设置 DB_PASSWORD）——强烈建议先手动备份！"
fi

# 2. 备份当前 JAR（回滚用）
OLD_JAR_BACKUP=""
if [[ -f "$APP_ROOT/$JAR_NAME" ]]; then
    OLD_JAR_BACKUP="$BACKUP_DIR/${JAR_NAME}.${STAMP}.bak"
    log "备份旧 JAR -> $OLD_JAR_BACKUP"
    cp "$APP_ROOT/$JAR_NAME" "$OLD_JAR_BACKUP"
fi

# 3. 替换 JAR
log "停止服务 $SERVICE"
systemctl stop "$SERVICE"
log "替换 JAR"
cp "$NEW_JAR" "$APP_ROOT/$JAR_NAME"
chown www-data:www-data "$APP_ROOT/$JAR_NAME" || true

# 4. 启动（Flyway 会在启动时自动迁移数据库）
log "启动服务（含 Flyway 自动迁移）"
systemctl start "$SERVICE"

# 5. 健康检查
log "健康检查：$HEALTH_URL"
healthy=false
for i in $(seq 1 "$HEALTH_RETRIES"); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" || true)"
    if [[ "$code" == "200" ]]; then
        healthy=true
        log "健康检查通过（第 ${i} 次，HTTP 200）"
        break
    fi
    echo "  ...等待就绪（第 ${i}/${HEALTH_RETRIES} 次，HTTP ${code}）"
    sleep "$HEALTH_INTERVAL"
done

# 6. 失败自动回滚
if [[ "$healthy" != "true" ]]; then
    err "健康检查失败，开始回滚！"
    systemctl stop "$SERVICE" || true
    if [[ -n "$OLD_JAR_BACKUP" && -f "$OLD_JAR_BACKUP" ]]; then
        cp "$OLD_JAR_BACKUP" "$APP_ROOT/$JAR_NAME"
        chown www-data:www-data "$APP_ROOT/$JAR_NAME" || true
        systemctl start "$SERVICE"
        err "已回滚到旧 JAR 并重启。"
        err "注意：数据库迁移不会自动回滚。若本次有结构变更导致问题，请用备份 $BACKUP_DIR/db_${DB_NAME}_${STAMP}.sql 手动恢复。"
    else
        err "无旧 JAR 备份可回滚，请手动处理。查看日志：journalctl -u $SERVICE -n 100 --no-pager"
    fi
    exit 1
fi

log "升级成功。版本信息："
curl -s "${HEALTH_URL%/health}/info" || true
echo
log "如需回滚：cp $OLD_JAR_BACKUP $APP_ROOT/$JAR_NAME && systemctl restart $SERVICE"

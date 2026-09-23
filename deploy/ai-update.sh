#!/bin/bash
# 服务器一键更新：git pull 后同步产物，不必从 Windows 上传 100MB+ JAR。
# 用法：
#   ai-update frontend          # 拉前端仓库，把已构建的 dist 同步到 Nginx
#   ai-update backend           # 拉后端仓库，在服务器 mvn 打包并重启
#   ai-update all
#   ai-update frontend-tarball  # 使用 /tmp/fe-dist.tgz（本机刚构建、尚未 push 时）
set -euo pipefail

FE_DIR=/opt/src/Ai-frontend
BE_DIR=/opt/src/Ai-Backend
WEB_ROOT=/var/www/ai-frontend
APP_ROOT=/opt/ai-backend
FE_REPO=https://github.com/mango-pie/Ai-frontend.git
BE_REPO=https://github.com/mango-pie/myWebsite.git
FE_BRANCH=${FE_BRANCH:-restore-polished-ui}
BE_BRANCH=${BE_BRANCH:-feature/module-decoupling}

show_index() {
  grep -oE 'assets/index-[A-Za-z0-9_.-]+' "$WEB_ROOT/index.html" | head -n 2 || true
}

ensure_repo() {
  local dir="$1" repo="$2" branch="$3"
  mkdir -p /opt/src
  if [[ ! -d "$dir/.git" ]]; then
    git clone --branch "$branch" --single-branch "$repo" "$dir"
  fi
  git -C "$dir" remote set-url origin "$repo"
  git -C "$dir" fetch origin "$branch"
  git -C "$dir" checkout "$branch"
  git -C "$dir" reset --hard "origin/$branch"
}

sync_web_root() {
  local src="$1"
  rsync -a --delete "$src/" "$WEB_ROOT/"
  chown -R www-data:www-data "$WEB_ROOT"
}

deploy_frontend_from_git() {
  ensure_repo "$FE_DIR" "$FE_REPO" "$FE_BRANCH"
  local dist="$FE_DIR/AI-frontend/dist"
  if [[ ! -f "$dist/index.html" ]]; then
    echo "dist missing, building on server..."
    cd "$FE_DIR/AI-frontend"
    npm ci
    export NODE_OPTIONS='--max-old-space-size=384'
    npm run build-only
  fi
  sync_web_root "$FE_DIR/AI-frontend/dist"
  echo -n "frontend git deploy ok: "
  show_index
}

deploy_frontend_tarball() {
  local tarfile=${1:-/tmp/fe-dist.tgz}
  [[ -f "$tarfile" ]] || { echo "missing $tarfile"; exit 1; }
  rm -rf /tmp/fe-dist
  mkdir -p /tmp/fe-dist
  tar -xzf "$tarfile" -C /tmp/fe-dist
  sync_web_root /tmp/fe-dist
  rm -rf /tmp/fe-dist
  echo -n "frontend tarball deploy ok: "
  show_index
}

deploy_backend() {
  ensure_repo "$BE_DIR" "$BE_REPO" "$BE_BRANCH"
  systemctl stop ai-backend
  export MAVEN_OPTS='-Xms64m -Xmx384m'
  cd "$BE_DIR"
  find src -name '*.java' -o -name '*.yml' | xargs -r sed -i 's/\r$//'
  mvn -DskipTests package
  STAMP=$(date +%Y%m%d%H%M)
  cp -a "$APP_ROOT/AI-0.0.1-SNAPSHOT.jar" "$APP_ROOT/AI-0.0.1-SNAPSHOT.jar.bak-$STAMP"
  cp target/AI-0.0.1-SNAPSHOT.jar "$APP_ROOT/AI-0.0.1-SNAPSHOT.jar"
  chown www-data:www-data "$APP_ROOT/AI-0.0.1-SNAPSHOT.jar"
  systemctl start ai-backend
  for i in $(seq 1 40); do
    code=$(curl -sS -m 3 -o /tmp/h.json -w '%{http_code}' http://127.0.0.1:8123/api/actuator/health || true)
    echo "try $i health=$code"
    if [[ "$code" == "200" ]]; then
      cat /tmp/h.json; echo
      echo "backend deploy ok"
      return 0
    fi
    sleep 3
  done
  journalctl -u ai-backend -n 80 --no-pager
  exit 2
}

cmd=${1:-}
case "$cmd" in
  frontend) deploy_frontend_from_git ;;
  frontend-tarball) deploy_frontend_tarball "${2:-/tmp/fe-dist.tgz}" ;;
  backend) deploy_backend ;;
  all) deploy_frontend_from_git; deploy_backend ;;
  *)
    echo "usage: ai-update frontend|backend|all|frontend-tarball [tgz]"
    exit 1
    ;;
esac

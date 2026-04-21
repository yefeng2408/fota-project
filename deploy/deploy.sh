#!/usr/bin/env bash

set -euo pipefail

PROJECT_NAME="fota-project"
BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

WEB_ADMIN_DIR="${BASE_DIR}/fota-web-admin"
WEB_HTTP_DIR="${BASE_DIR}/fota-web-http-server"
GATEWAY_DIR="${BASE_DIR}/fota-gateway-tcp-server"
MOCK_DEVICE_DIR="${BASE_DIR}/fota-mock-device-client"

COMPOSE_FILE="${BASE_DIR}/docker-compose.yml"

log() {
  echo ""
  echo "=================================================="
  echo "[INFO] $1"
  echo "=================================================="
}

check_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[ERROR] 未找到命令: $1"
    exit 1
  }
}

check_dir() {
  if [ ! -d "$1" ]; then
    echo "[ERROR] 目录不存在: $1"
    exit 1
  fi
}

check_file() {
  if [ ! -f "$1" ]; then
    echo "[ERROR] 文件不存在: $1"
    exit 1
  fi
}

log "检查环境"
check_command docker
check_command mvn
check_command npm

check_dir "${WEB_ADMIN_DIR}"
check_dir "${WEB_HTTP_DIR}"
check_dir "${GATEWAY_DIR}"
check_dir "${MOCK_DEVICE_DIR}"
check_file "${COMPOSE_FILE}"

log "创建运行目录"
mkdir -p "${BASE_DIR}/data/mysql"
mkdir -p "${BASE_DIR}/data/redis"
mkdir -p "${BASE_DIR}/data/minio"
mkdir -p "${BASE_DIR}/docker/mysql/init"
mkdir -p "${BASE_DIR}/docker/nginx/conf.d"

log "前端构建"
cd "${WEB_ADMIN_DIR}"
npm install
npm run build

if [ ! -d "${WEB_ADMIN_DIR}/dist" ]; then
  echo "[ERROR] 前端 dist 目录不存在，构建失败"
  exit 1
fi

log "Maven 打包"
cd "${BASE_DIR}"
mvn clean package -DskipTests

log "构建 Docker 镜像"
docker build -t fota-web-http-server:latest -f "${WEB_HTTP_DIR}/Dockerfile" "${BASE_DIR}"
docker build -t fota-gateway-tcp-server:latest -f "${GATEWAY_DIR}/Dockerfile" "${BASE_DIR}"
docker build -t fota-mock-device-client:latest -f "${MOCK_DEVICE_DIR}/Dockerfile" "${BASE_DIR}"

log "停止旧容器"
docker compose -f "${COMPOSE_FILE}" down

log "启动新容器"
docker compose -f "${COMPOSE_FILE}" up -d

log "查看容器状态"
docker compose -f "${COMPOSE_FILE}" ps

log "部署完成"
echo "前端访问: http://<你的服务器IP>/"
echo "MinIO 控制台: http://<你的服务器IP>:9001"
echo "Gateway TCP 端口: 8224"
#!/usr/bin/env bash

set -euo pipefail

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="${BASE_DIR}/docker-compose.yml"
ENV_FILE="${BASE_DIR}/.env"

WEB_ADMIN_DIR="${BASE_DIR}/fota-web-admin"
WEB_HTTP_DIR="${BASE_DIR}/fota-web-http-server"
GATEWAY_DIR="${BASE_DIR}/fota-gateway-tcp-server"
MOCK_DEVICE_DIR="${BASE_DIR}/fota-mock-device-client"

readonly IMAGE_WEB="fota-web-http-server:latest"
readonly IMAGE_GATEWAY="fota-gateway-tcp-server:latest"
readonly IMAGE_MOCK="fota-mock-device-client:latest"
readonly HEALTH_TIMEOUT=180
readonly HEALTH_INTERVAL=5

log() {
  echo ""
  echo "=================================================="
  echo "[INFO] $1"
  echo "=================================================="
}

warn() {
  echo "[WARN] $1"
}

error_exit() {
  echo "[ERROR] $1"
  exit 1
}

check_command() {
  command -v "$1" >/dev/null 2>&1 || error_exit "未找到命令: $1"
}

check_dir() {
  [ -d "$1" ] || error_exit "目录不存在: $1"
}

check_file() {
  [ -f "$1" ] || error_exit "文件不存在: $1"
}

load_env() {
  if [ -f "${ENV_FILE}" ]; then
    log "加载环境变量 .env"
    set -a
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
    set +a
  else
    warn ".env 文件不存在，将使用 docker-compose.yml 中的默认配置或外部环境变量"
  fi
}

prepare_dirs() {
  log "创建运行目录"
  mkdir -p "${BASE_DIR}/data/mysql"
  mkdir -p "${BASE_DIR}/data/redis"
  mkdir -p "${BASE_DIR}/data/minio"
  mkdir -p "${BASE_DIR}/logs/web"
  mkdir -p "${BASE_DIR}/logs/gateway"
  mkdir -p "${BASE_DIR}/logs/mock"
  mkdir -p "${BASE_DIR}/docker/mysql/init"
  mkdir -p "${BASE_DIR}/docker/nginx/conf.d"
}

build_frontend() {
  log "前端构建"
  cd "${WEB_ADMIN_DIR}"
  npm install
  npm run build
  [ -d "${WEB_ADMIN_DIR}/dist" ] || error_exit "前端 dist 目录不存在，构建失败"
}

build_backend() {
  log "Maven 打包"
  cd "${BASE_DIR}"
  mvn clean package -DskipTests
}

build_images() {
  log "构建 Docker 镜像"
  docker build -t "${IMAGE_WEB}" -f "${WEB_HTTP_DIR}/Dockerfile" "${BASE_DIR}"
  docker build -t "${IMAGE_GATEWAY}" -f "${GATEWAY_DIR}/Dockerfile" "${BASE_DIR}"
  docker build -t "${IMAGE_MOCK}" -f "${MOCK_DEVICE_DIR}/Dockerfile" "${BASE_DIR}"
}

compose_down() {
  log "停止旧容器"
  docker compose -f "${COMPOSE_FILE}" down
}

compose_up() {
  log "启动新容器"
  docker compose -f "${COMPOSE_FILE}" up -d
}

container_id() {
  docker compose -f "${COMPOSE_FILE}" ps -q "$1"
}

is_container_running() {
  local container_id="$1"
  [ -n "${container_id}" ] || return 1
  local running
  running="$(docker inspect -f '{{.State.Running}}' "${container_id}" 2>/dev/null || echo false)"
  [ "${running}" = "true" ]
}

wait_for_container() {
  local service_name="$1"
  local timeout="${2:-$HEALTH_TIMEOUT}"
  local elapsed=0

  log "等待服务启动: ${service_name}"
  while [ "${elapsed}" -lt "${timeout}" ]; do
    local cid
    cid="$(container_id "${service_name}")"
    if is_container_running "${cid}"; then
      echo "[INFO] 服务已启动: ${service_name}"
      return 0
    fi
    sleep "${HEALTH_INTERVAL}"
    elapsed=$((elapsed + HEALTH_INTERVAL))
  done

  docker compose -f "${COMPOSE_FILE}" logs --tail=100 "${service_name}" || true
  error_exit "服务启动超时: ${service_name}"
}

show_status() {
  log "查看容器状态"
  docker compose -f "${COMPOSE_FILE}" ps
}

show_recent_logs() {
  log "输出最近日志（web / gateway / mock）"
  docker compose -f "${COMPOSE_FILE}" logs --tail=50 fota-web-http-server || true
  docker compose -f "${COMPOSE_FILE}" logs --tail=50 fota-gateway-tcp-server || true
  docker compose -f "${COMPOSE_FILE}" logs --tail=50 fota-mock-device-client || true
}

print_summary() {
  log "部署完成"
  echo "前端访问: http://<你的服务器IP>/"
  echo "MinIO 控制台: http://<你的服务器IP>:9001"
  echo "Gateway TCP 端口: 8224"
  echo "若前端或实时推送异常，请优先检查:"
  echo "1) Nginx /api 与 /ws 反向代理配置"
  echo "2) fota-web-http-server 是否正常启动"
  echo "3) fota-gateway-tcp-server 是否正常监听 8224"
}

main() {
  log "检查环境"
  check_command docker
  check_command mvn
  check_command npm

  check_dir "${WEB_ADMIN_DIR}"
  check_dir "${WEB_HTTP_DIR}"
  check_dir "${GATEWAY_DIR}"
  check_dir "${MOCK_DEVICE_DIR}"
  check_file "${COMPOSE_FILE}"

  load_env
  prepare_dirs
  build_frontend
  build_backend
  build_images
  compose_down
  compose_up

  wait_for_container mysql 120
  wait_for_container redis 120
  wait_for_container minio 120
  wait_for_container fota-web-http-server 180
  wait_for_container fota-gateway-tcp-server 180
  wait_for_container fota-mock-device-client 180
  wait_for_container nginx 120

  show_status
  show_recent_logs
  print_summary
}

main "$@"
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${BASE_DIR}/docker-compose.yml"
ENV_FILE="${BASE_DIR}/.env"

HEALTH_TIMEOUT=240
HEALTH_INTERVAL=5
WITH_MOCK=false
REBUILD=true
PULL_IMAGES=false

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

usage() {
  cat <<'EOF'
用法:
  ./deploy/deploy.sh [选项]

选项:
  --with-mock    启动 fota-mock-device-client
  --no-build     启动时不重新构建镜像
  --pull         启动前拉取基础镜像
  -h, --help     查看帮助

说明:
  1. 默认只部署 mysql / redis / minio / web / gateway / nginx
  2. 默认执行 docker compose up -d --build
  3. 若需要模拟设备，请加 --with-mock
EOF
}

parse_args() {
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --with-mock)
        WITH_MOCK=true
        ;;
      --no-build)
        REBUILD=false
        ;;
      --pull)
        PULL_IMAGES=true
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        error_exit "未知参数: $1"
        ;;
    esac
    shift
  done
}

check_command() {
  command -v "$1" >/dev/null 2>&1 || error_exit "未找到命令: $1"
}

check_file() {
  [ -f "$1" ] || error_exit "文件不存在: $1"
}

check_docker() {
  docker info >/dev/null 2>&1 || error_exit "Docker 未启动或当前用户无权限访问 Docker"
}

check_compose() {
  docker compose version >/dev/null 2>&1 || error_exit "未检测到 docker compose plugin"
}

compose() {
  docker compose -f "${COMPOSE_FILE}" "$@"
}

load_env() {
  [ -f "${ENV_FILE}" ] || error_exit ".env 文件不存在，请先执行: cp .env.example .env 并修改生产密码"

  log "加载环境变量 .env"
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
}

prepare_dirs() {
  log "创建运行目录"
  mkdir -p "${BASE_DIR}/data/mysql"
  mkdir -p "${BASE_DIR}/data/redis"
  mkdir -p "${BASE_DIR}/data/minio"
  mkdir -p "${BASE_DIR}/docker/mysql/init"
}

validate_compose() {
  log "校验 docker compose 配置"
  compose config >/dev/null
}

pull_images() {
  if [ "${PULL_IMAGES}" != "true" ]; then
    return
  fi
  log "拉取基础镜像"
  compose pull mysql redis minio || true
}

compose_down() {
  log "停止旧容器"
  compose down --remove-orphans
}

compose_up() {
  local args=(up -d)
  if [ "${REBUILD}" = "true" ]; then
    args+=(--build)
  fi
  if [ "${WITH_MOCK}" = "true" ]; then
    log "启动服务（包含 mock profile）"
    compose --profile mock "${args[@]}"
  else
    log "启动服务（生产默认，不包含 mock）"
    compose "${args[@]}"
  fi
}

container_id() {
  compose ps -q "$1"
}

container_state() {
  local cid="$1"
  docker inspect -f '{{.State.Status}}' "${cid}" 2>/dev/null || true
}

container_health() {
  local cid="$1"
  docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${cid}" 2>/dev/null || true
}

wait_for_service() {
  local service_name="$1"
  local timeout="${2:-$HEALTH_TIMEOUT}"
  local elapsed=0

  log "等待服务就绪: ${service_name}"
  while [ "${elapsed}" -lt "${timeout}" ]; do
    local cid
    cid="$(container_id "${service_name}")"

    if [ -n "${cid}" ]; then
      local state health
      state="$(container_state "${cid}")"
      health="$(container_health "${cid}")"

      if [ "${state}" = "running" ]; then
        if [ "${health}" = "healthy" ] || [ "${health}" = "none" ]; then
          echo "[INFO] 服务已就绪: ${service_name} (state=${state}, health=${health})"
          return 0
        fi
      fi

      if [ "${state}" = "exited" ] || [ "${state}" = "dead" ]; then
        compose logs --tail=100 "${service_name}" || true
        error_exit "服务启动失败: ${service_name}"
      fi
    fi

    sleep "${HEALTH_INTERVAL}"
    elapsed=$((elapsed + HEALTH_INTERVAL))
  done

  compose logs --tail=100 "${service_name}" || true
  error_exit "等待服务超时: ${service_name}"
}

wait_for_services() {
  wait_for_service mysql 180
  wait_for_service redis 120
  wait_for_service minio 180
  wait_for_service fota-web-http-server 240
  wait_for_service fota-gateway-tcp-server 240
  wait_for_service nginx 120

  if [ "${WITH_MOCK}" = "true" ]; then
    wait_for_service fota-mock-device-client 180
  fi
}

show_status() {
  log "当前容器状态"
  compose ps
}

show_logs() {
  log "最近日志（web / gateway / nginx）"
  compose logs --tail=60 fota-web-http-server || true
  compose logs --tail=60 fota-gateway-tcp-server || true
  compose logs --tail=60 nginx || true

  if [ "${WITH_MOCK}" = "true" ]; then
    compose logs --tail=60 fota-mock-device-client || true
  fi
}

print_summary() {
  log "部署完成"
  echo "项目根目录: ${BASE_DIR}"
  echo "前端访问: http://<你的服务器IP>/"
  echo "Gateway TCP 端口: 8224"
  echo "MinIO 控制台: http://<你的服务器IP>:9001"
  echo ""
  echo "建议上线前确认:"
  echo "1) 已修改 MySQL / MinIO / JWT 生产密码"
  echo "2) 腾讯云安全组已放行 22 / 80 / 443 / 8224"
  echo "3) 3306 / 6379 / 8080 / 8081 / 9000 / 9001 未对公网开放或已限制来源 IP"
}

main() {
  parse_args "$@"

  log "检查环境"
  check_command docker
  check_command git
  check_file "${COMPOSE_FILE}"
  check_docker
  check_compose

  cd "${BASE_DIR}"
  load_env
  prepare_dirs
  validate_compose
  pull_images
  compose_down
  compose_up
  wait_for_services
  show_status
  show_logs
  print_summary
}

main "$@"

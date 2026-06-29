#!/usr/bin/env bash
# ============================================================================
# 端到端测试总编排脚本
#
# 流程：启动后端 → 就绪检查 → 启动前端 → 就绪检查 →
#       Layer 1 (API 链路) → Layer 2 (Playwright UI) → 汇总
#
# 中间件使用 NAS 现有环境（后端默认 profile：
#   PG 192.168.1.2:5532 / Redis 192.168.1.2:6380）
#
# 用法：bash e2e/run-e2e.sh
# 退出码：0 全部通过；非 0 表示有失败或环境异常
# ============================================================================
set -uo pipefail

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; BOLD='\033[1m'; RESET='\033[0m'

# 绕过代理：测试目标都是 localhost，本机若有全局 HTTP_PROXY（如 Clash）会返回 502。
export NO_PROXY='localhost,127.0.0.1'
export no_proxy='localhost,127.0.0.1'
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY 2>/dev/null || true

# 项目根目录（脚本位于 <root>/e2e/）
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
E2E_DIR="${ROOT}/e2e"
LOG_DIR="${E2E_DIR}/logs"
mkdir -p "$LOG_DIR"

BACKEND_JAR="${ROOT}/backend/app/target/app-1.0.0-SNAPSHOT.jar"
BACKEND_PORT=8090
FRONTEND_PORT=5173
BACKEND_URL="http://localhost:${BACKEND_PORT}"
FRONTEND_URL="http://localhost:${FRONTEND_PORT}"

# 数据库 / Redis 连接覆盖（可选）。默认留空，使用 application.yml 里的配置。
# 通过 Spring 的 relaxed binding 环境变量覆盖，不修改任何源文件。
# 例：DB_PASSWORD=Postgres@2025 bash e2e/run-e2e.sh
declare -a JAVA_OPTS=()
[ -n "${DB_URL:-}" ]          && JAVA_OPTS+=("-Dspring.datasource.url=${DB_URL}")
[ -n "${DB_USERNAME:-}" ]     && JAVA_OPTS+=("-Dspring.datasource.username=${DB_USERNAME}")
[ -n "${DB_PASSWORD:-}" ]     && JAVA_OPTS+=("-Dspring.datasource.password=${DB_PASSWORD}")
[ -n "${REDIS_HOST:-}" ]      && JAVA_OPTS+=("-Dspring.data.redis.host=${REDIS_HOST}")
[ -n "${REDIS_PORT:-}" ]      && JAVA_OPTS+=("-Dspring.data.redis.port=${REDIS_PORT}")
[ -n "${REDIS_PASSWORD:-}" ]  && JAVA_OPTS+=("-Dspring.data.redis.password=${REDIS_PASSWORD}")
[ -n "${SPRING_PROFILES:-}" ] && JAVA_OPTS+=("-Dspring.profiles.active=${SPRING_PROFILES}")

# 默认禁用 Redis 健康指标：当前业务代码不依赖 Redis（仅引入了 starter），
# NAS 上的 Redis 需要密码但密码不在项目配置中，禁用后 /actuator/health 不再因 Redis 报 DOWN。
# 若提供了 REDIS_PASSWORD，则保持开启（业务真要用 Redis 时）。
if [ -z "${REDIS_PASSWORD:-}" ]; then
  JAVA_OPTS+=("-Dmanagement.health.redis.enabled=false")
fi

PIDS=()
OVERALL_RC=0

# ---------------------------------------------------------------------------
# 清理：退出时 kill 所有后台进程
# ---------------------------------------------------------------------------
cleanup() {
  echo ""
  echo -e "${YELLOW}>>> 清理后台进程${RESET}"
  # set -u 下空数组展开需容错
  if [ "${#PIDS[@]}" -gt 0 ]; then
    for pid in "${PIDS[@]}"; do
      if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null || true
        echo "  已终止 pid=$pid"
      fi
    done
  fi
  # 兜底：按端口清理（防止进程已 fork）
  for port in "$BACKEND_PORT" "$FRONTEND_PORT"; do
    lsof -ti tcp:"$port" 2>/dev/null | xargs kill 2>/dev/null || true
  done
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# 轮询就绪：第一个参数为 URL，第二个为超时秒数
# ---------------------------------------------------------------------------
wait_for() {
  local url="$1" timeout="$2" label="$3"
  local elapsed=0
  echo -e "${YELLOW}>>> 等待 ${label} 就绪（最多 ${timeout}s）${RESET}"
  while [ "$elapsed" -lt "$timeout" ]; do
    if curl -sf -o /dev/null "$url" 2>/dev/null; then
      echo -e "${GREEN}>>> ${label} 已就绪（${elapsed}s）${RESET}"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo -e "${RED}>>> ${label} 在 ${timeout}s 内未就绪${RESET}"
  return 1
}

# ---------------------------------------------------------------------------
# 后端健康检查：/actuator/health 返回 {"status":"UP"}
# ---------------------------------------------------------------------------
wait_for_backend_health() {
  local timeout="$1" elapsed=0
  echo -e "${YELLOW}>>> 等待后端健康检查 UP（最多 ${timeout}s）${RESET}"
  while [ "$elapsed" -lt "$timeout" ]; do
    local status
    status=$(curl -sf "${BACKEND_URL}/actuator/health" 2>/dev/null | jq -r '.status // empty' 2>/dev/null)
    if [ "$status" = "UP" ]; then
      echo -e "${GREEN}>>> 后端健康检查 UP（${elapsed}s）${RESET}"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo -e "${RED}>>> 后端在 ${timeout}s 内未 UP${RESET}"
  return 1
}

echo -e "${BOLD}=============== 端到端测试 ===============${RESET}"
echo "项目根目录: ${ROOT}"

# ---------------------------------------------------------------------------
# 0. 前置检查：jar 存在 + 端口空闲 + 工具可用
# ---------------------------------------------------------------------------
command -v curl >/dev/null 2>&1 || { echo -e "${RED}缺少 curl${RESET}"; exit 2; }
command -v jq   >/dev/null 2>&1 || { echo -e "${RED}缺少 jq${RESET}"; exit 2; }

if [ ! -f "$BACKEND_JAR" ]; then
  echo -e "${RED}后端 jar 不存在：${BACKEND_JAR}${RESET}"
  echo -e "请先执行：cd backend && mvn package -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true -Djacoco.skip=true"
  exit 2
fi

for port in "$BACKEND_PORT" "$FRONTEND_PORT"; do
  if lsof -ti tcp:"$port" >/dev/null 2>&1; then
    echo -e "${YELLOW}>>> 端口 ${port} 已被占用，复用现有服务${RESET}"
  fi
done

# ---------------------------------------------------------------------------
# 1. 启动后端
# ---------------------------------------------------------------------------
if ! lsof -ti tcp:"$BACKEND_PORT" >/dev/null 2>&1; then
  echo -e "${BOLD}--- [1/4] 启动后端 ---${RESET}"
  echo "  jar: ${BACKEND_JAR}"
  if [ "${#JAVA_OPTS[@]}" -gt 0 ]; then
    java "${JAVA_OPTS[@]}" -jar "$BACKEND_JAR" >"${LOG_DIR}/backend.log" 2>&1 &
  else
    java -jar "$BACKEND_JAR" >"${LOG_DIR}/backend.log" 2>&1 &
  fi
  BACKEND_PID=$!
  PIDS+=("$BACKEND_PID")
  echo "  后端 pid=${BACKEND_PID}"
  wait_for_backend_health 90 || { echo -e "${RED}后端启动失败，见 ${LOG_DIR}/backend.log${RESET}"; OVERALL_RC=1; exit 1; }
else
  echo -e "${BOLD}--- [1/4] 后端已在运行，跳过启动 ---${RESET}"
fi

# ---------------------------------------------------------------------------
# 2. 启动前端 dev server
# ---------------------------------------------------------------------------
if ! lsof -ti tcp:"$FRONTEND_PORT" >/dev/null 2>&1; then
  echo -e "${BOLD}--- [2/4] 启动前端 dev server ---${RESET}"
  (cd "${ROOT}/frontend" && npm run dev >"${LOG_DIR}/frontend.log" 2>&1) &
  FRONTEND_PID=$!
  PIDS+=("$FRONTEND_PID")
  echo "  前端 pid=${FRONTEND_PID}"
  wait_for "$FRONTEND_URL" 60 "前端 dev server" || { echo -e "${RED}前端启动失败，见 ${LOG_DIR}/frontend.log${RESET}"; OVERALL_RC=1; exit 1; }
else
  echo -e "${BOLD}--- [2/4] 前端已在运行，跳过启动 ---${RESET}"
fi

# ---------------------------------------------------------------------------
# 3. Layer 1：API 链路 e2e
# ---------------------------------------------------------------------------
echo -e "${BOLD}--- [3/4] Layer 1: API 链路 e2e ---${RESET}"
LAYER1_RC=0
bash "${E2E_DIR}/api-e2e.sh" "$BACKEND_URL" || LAYER1_RC=$?
if [ "$LAYER1_RC" -ne 0 ]; then
  echo -e "${RED}Layer 1 存在失败${RESET}"
  OVERALL_RC=1
else
  echo -e "${GREEN}Layer 1 全部通过${RESET}"
fi

# ---------------------------------------------------------------------------
# 4. Layer 2：Playwright UI e2e
# ---------------------------------------------------------------------------
echo -e "${BOLD}--- [4/4] Layer 2: Playwright UI e2e ---${RESET}"
# @playwright/test 装在 frontend/node_modules，e2e 配置在项目根下的 e2e/，
# 通过 NODE_PATH 让配置文件能解析到依赖，--prefix 复用 frontend 的 playwright CLI。
LAYER2_RC=0
(cd "${E2E_DIR}" \
  && NODE_PATH="${ROOT}/frontend/node_modules" \
     npx --prefix "${ROOT}/frontend" playwright test --reporter=list) || LAYER2_RC=$?
if [ "$LAYER2_RC" -ne 0 ]; then
  echo -e "${RED}Layer 2 存在失败${RESET}"
  OVERALL_RC=1
else
  echo -e "${GREEN}Layer 2 全部通过${RESET}"
fi

# ---------------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------------
echo ""
echo -e "${BOLD}=============== 端到端测试汇总 ===============${RESET}"
[ "$LAYER1_RC" -eq 0 ] && echo -e "Layer 1 (API 链路):  ${GREEN}PASS${RESET}" || echo -e "Layer 1 (API 链路):  ${RED}FAIL${RESET}"
[ "$LAYER2_RC" -eq 0 ] && echo -e "Layer 2 (Playwright): ${GREEN}PASS${RESET}" || echo -e "Layer 2 (Playwright): ${RED}FAIL${RESET}"
echo "日志目录: ${LOG_DIR}/"
echo -e "${BOLD}=============================================${RESET}"

exit $OVERALL_RC

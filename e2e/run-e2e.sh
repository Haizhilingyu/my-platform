#!/usr/bin/env bash
# ============================================================================
# E2E 测试总编排脚本
#
# 流程：
#   1. 启动 docker-compose 本地栈（postgres + redis + app 合并镜像）
#   2. 等待应用就绪
#   3. e2e 目录 npm install + npx playwright install
#   4. 运行 Playwright UI + DB 集成测试（Layer 2）
#   5. 汇总退出
#
# 中间件连接信息（默认）：
#   PostgreSQL localhost:5533  user=postgres pass=${PG_PASSWORD:-changeme} db=platform
#   Redis     localhost:6381   (no password)
#   App       localhost:8090   (合并镜像，SPA + API + WebSocket)
#
# 用法：bash e2e/run-e2e.sh [--keep-stack]
#   --keep-stack  测试结束后不销毁 docker compose 栈，便于本地调试
# 退出码：0 全部通过；非 0 表示失败或环境异常
# ============================================================================
set -uo pipefail

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; BOLD='\033[1m'; RESET='\033[0m'

# 绕过代理：测试目标都是 localhost，本机若有全局 HTTP_PROXY（如 Clash）会返回 502
export NO_PROXY='localhost,127.0.0.1'
export no_proxy='localhost,127.0.0.1'
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY 2>/dev/null || true

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
E2E_DIR="${ROOT}/e2e"
DOCKER_DIR="${ROOT}/docker"
LOG_DIR="${E2E_DIR}/logs"
mkdir -p "$LOG_DIR"

APP_URL="http://localhost:8090"
KEEP_STACK=false
for arg in "$@"; do
  case "$arg" in
    --keep-stack) KEEP_STACK=true ;;
  esac
done

OVERALL_RC=0

cleanup() {
  echo ""
  if [ "$KEEP_STACK" = "true" ]; then
    echo -e "${YELLOW}>>> --keep-stack 已指定，保留 docker compose 栈${RESET}"
  else
    echo -e "${YELLOW}>>> 清理 docker compose 栈${RESET}"
    (cd "$DOCKER_DIR" && docker compose -f docker-compose.local.yml down 2>/dev/null || true)
  fi
}
trap cleanup EXIT

echo -e "${BOLD}=============== E2E 测试 ===============${RESET}"
echo "项目根目录: ${ROOT}"

command -v curl >/dev/null 2>&1 || { echo -e "${RED}缺少 curl${RESET}"; exit 2; }
command -v docker >/dev/null 2>&1 || { echo -e "${RED}缺少 docker${RESET}"; exit 2; }

# ---------------------------------------------------------------------------
# 1. 启动 docker compose 栈
# ---------------------------------------------------------------------------
echo -e "${BOLD}--- [1/4] 启动 docker compose 栈 ---${RESET}"
cd "$DOCKER_DIR"
docker compose -f docker-compose.local.yml down -v 2>/dev/null || true
docker compose -f docker-compose.local.yml up -d --build || {
  echo -e "${RED}docker compose 启动失败${RESET}"
  OVERALL_RC=1
  exit 1
}
cd "$E2E_DIR"

# ---------------------------------------------------------------------------
# 2. 等待应用就绪
# ---------------------------------------------------------------------------
echo -e "${BOLD}--- [2/4] 等待应用就绪 ---${RESET}"
READY=false
for i in $(seq 1 60); do
  if curl -sf "${APP_URL}/api/sys/auth/login-methods" >/dev/null 2>&1; then
    echo -e "${GREEN}>>> 应用已就绪（${i}*2s）${RESET}"
    READY=true
    break
  fi
  sleep 2
done
if [ "$READY" != "true" ]; then
  echo -e "${RED}>>> 应用在 120s 内未就绪${RESET}"
  docker compose -f "$DOCKER_DIR/docker-compose.local.yml" logs app | tail -50 || true
  OVERALL_RC=1
  exit 1
fi

# ---------------------------------------------------------------------------
# 3. 安装 E2E 依赖
# ---------------------------------------------------------------------------
echo -e "${BOLD}--- [3/4] 安装 E2E 依赖 ---${RESET}"
cd "$E2E_DIR"
if [ ! -d node_modules ]; then
  npm install || { echo -e "${RED}npm install 失败${RESET}"; OVERALL_RC=1; exit 1; }
fi
npx playwright install chromium || { echo -e "${RED}playwright 浏览器安装失败${RESET}"; OVERALL_RC=1; exit 1; }

# ---------------------------------------------------------------------------
# 4. 运行 Playwright 测试
# ---------------------------------------------------------------------------
echo -e "${BOLD}--- [4/4] Playwright 测试 ---${RESET}"
TEST_RC=0
npx playwright test --reporter=list "$@" 2>&1 | tee "$LOG_DIR/playwright.log" || TEST_RC=$?

# 过滤掉 --keep-stack，避免传给 playwright
if [ "$TEST_RC" -ne 0 ]; then
  echo -e "${RED}>>> Playwright 测试存在失败${RESET}"
  OVERALL_RC=1
else
  echo -e "${GREEN}>>> Playwright 测试全部通过${RESET}"
fi

echo ""
echo -e "${BOLD}=============== E2E 汇总 ===============${RESET}"
[ "$OVERALL_RC" -eq 0 ] && echo -e "结果: ${GREEN}PASS${RESET}" || echo -e "结果: ${RED}FAIL${RESET}"
echo "日志: ${LOG_DIR}/playwright.log"
echo -e "${BOLD}========================================${RESET}"

exit $OVERALL_RC

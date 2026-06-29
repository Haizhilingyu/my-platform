#!/usr/bin/env bash
# ============================================================================
# Layer 1: API 链路端到端测试
# 直连后端 8090，覆盖 Flyway+PG / Redis / JWT / 权限 全链路。
# 依赖：curl + jq。前置：后端已启动并通过 /actuator/health 健康检查。
# 用法：bash e2e/api-e2e.sh [BACKEND_BASE_URL]   默认 http://localhost:8090
# ============================================================================
set -uo pipefail

BASE="${1:-http://localhost:8090}"
PASS=0
FAIL=0
FAILED_CASES=()

# 绕过代理：目标是 localhost，本机若有全局 HTTP_PROXY（如 Clash）会返回 502。
export NO_PROXY='localhost,127.0.0.1'
export no_proxy='localhost,127.0.0.1'
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY 2>/dev/null || true

# 彩色输出
GREEN='\033[0;32m'; RED='\033[0;31m'; BOLD='\033[1m'; RESET='\033[0m'

# 断言辅助：传入描述、退出码(0=通过)，统一计数与打印
assert_case() {
  local desc="$1" ok="$2"
  if [ "$ok" -eq 0 ]; then
    printf "${GREEN}  ✓ PASS${RESET} %s\n" "$desc"
    PASS=$((PASS + 1))
  else
    printf "${RED}  ✗ FAIL${RESET} %s\n" "$desc"
    FAIL=$((FAIL + 1))
    FAILED_CASES+=("$desc")
  fi
}

echo -e "${BOLD}=== Layer 1: API 链路 e2e（后端 ${BASE}）===${RESET}"

command -v jq >/dev/null 2>&1 || { echo "${RED}错误：未安装 jq${RESET}"; exit 2; }

# ------------------------------------------------------------------
# 用例 1：无 token 访问受保护接口 → 期望 403
# Spring Security 6 在 anyRequest().authenticated() 且无自定义 entry point 时，
# 未认证访问直接返回 403（空 body）。这证明安全过滤生效。
# ------------------------------------------------------------------
code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE}/sys/auth/me")
assert_case "无 token 访问 /sys/auth/me 应返回 403（安全过滤生效）" \
  $([ "$code" = "403" ] && echo 0 || echo 1)

# ------------------------------------------------------------------
# 用例 2：错误密码登录 → 期望 401 + 业务错误 JSON
# ------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' -X POST "${BASE}/sys/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"wrong-password"}')
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
err_code=$(echo "$body" | jq -r '.code // empty' 2>/dev/null)
err_msg=$(echo "$body" | jq -r '.message // empty' 2>/dev/null)
assert_case "错误密码登录应返回 401" $([ "$code" = "401" ] && echo 0 || echo 1)
assert_case "错误密码 body.code 应为 401" $([ "$err_code" = "401" ] && echo 0 || echo 1)
assert_case "错误密码 message 应含『用户名或密码错误』" \
  $([ -n "$err_msg" ] && echo "$err_msg" | grep -q "用户名或密码错误" && echo 0 || echo 1)

# ------------------------------------------------------------------
# 用例 3：正确登录 admin/admin123 → 200 + LoginVO 结构
# ------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' -X POST "${BASE}/sys/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}')
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
TOKEN=$(echo "$body" | jq -r '.data.token // empty' 2>/dev/null)
token_type=$(echo "$body" | jq -r '.data.tokenType // empty' 2>/dev/null)
login_user=$(echo "$body" | jq -r '.data.user.username // empty' 2>/dev/null)
res_code=$(echo "$body" | jq -r '.code // empty' 2>/dev/null)
assert_case "正确登录应返回 200" $([ "$code" = "200" ] && echo 0 || echo 1)
assert_case "登录 body.code 应为 200" $([ "$res_code" = "200" ] && echo 0 || echo 1)
assert_case "登录 token 应非空" $([ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] && echo 0 || echo 1)
assert_case "登录 tokenType 应为 Bearer" $([ "$token_type" = "Bearer" ] && echo 0 || echo 1)
assert_case "登录 user.username 应为 admin" $([ "$login_user" = "admin" ] && echo 0 || echo 1)

# 拿不到 token，后续用例无法进行
if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo -e "${RED}登录未取得 token，中止后续用例${RESET}"
  echo -e "${BOLD}结果：${PASS} passed, ${FAIL} failed${RESET}"
  exit 1
fi

# ------------------------------------------------------------------
# 用例 4：带 token 调 /sys/auth/me → 200, user.id==1
# ------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/sys/auth/me" \
  -H "Authorization: Bearer ${TOKEN}")
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
me_id=$(echo "$body" | jq -r '.data.id // empty' 2>/dev/null)
me_user=$(echo "$body" | jq -r '.data.username // empty' 2>/dev/null)
assert_case "带 token 调 /sys/auth/me 应返回 200" $([ "$code" = "200" ] && echo 0 || echo 1)
assert_case "/sys/auth/me user.id 应为 1" $([ "$me_id" = "1" ] && echo 0 || echo 1)
assert_case "/sys/auth/me user.username 应为 admin" $([ "$me_user" = "admin" ] && echo 0 || echo 1)

# ------------------------------------------------------------------
# 用例 5：带 token 调 /sys/auth/permissions → 200, 权限集非空
# ------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/sys/auth/permissions" \
  -H "Authorization: Bearer ${TOKEN}")
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
perm_count=$(echo "$body" | jq '.data | length' 2>/dev/null)
assert_case "带 token 调 /sys/auth/permissions 应返回 200" $([ "$code" = "200" ] && echo 0 || echo 1)
assert_case "权限集应非空" $([ -n "$perm_count" ] && [ "$perm_count" -gt 0 ] && echo 0 || echo 1)

# ------------------------------------------------------------------
# 用例 6：带 token 调 /sys/auth/menus → 200, 菜单树含「系统管理」
# ------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/sys/auth/menus" \
  -H "Authorization: Bearer ${TOKEN}")
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
has_sys=$(echo "$body" | jq -r '[.data[]?.menuName] | index("系统管理") != null' 2>/dev/null)
assert_case "带 token 调 /sys/auth/menus 应返回 200" $([ "$code" = "200" ] && echo 0 || echo 1)
assert_case "菜单树应含『系统管理』目录" $([ "$has_sys" = "true" ] && echo 0 || echo 1)

# ------------------------------------------------------------------
# 用例 7：带 token 调 /sys/user → 200, 列表含 admin
# ------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/sys/user" \
  -H "Authorization: Bearer ${TOKEN}")
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
# 分页或数组结构都兼容：在整段 body 里找 username == admin
has_admin=$(echo "$body" | jq -r '[.. | .username? // empty] | index("admin") != null' 2>/dev/null)
assert_case "带 token 调 /sys/user（有权限）应返回 200" $([ "$code" = "200" ] && echo 0 || echo 1)
assert_case "用户列表应含 admin" $([ "$has_admin" = "true" ] && echo 0 || echo 1)

# ------------------------------------------------------------------
# 用例 8：无效 token 访问受保护接口 → 期望 403
# 无效 token 同样被安全层拒绝（Spring Security 6 默认 403）。
# ------------------------------------------------------------------
code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE}/sys/auth/me" \
  -H "Authorization: Bearer an.invalid.token")
assert_case "无效 token 访问受保护接口应返回 403" \
  $([ "$code" = "403" ] && echo 0 || echo 1)

# ------------------------------------------------------------------
# 汇总
# ------------------------------------------------------------------
echo ""
echo -e "${BOLD}=== Layer 1 结果：${GREEN}${PASS} passed${RESET}${BOLD}, ${RED}${FAIL} failed${RESET}${BOLD} ===${RESET}"
if [ "$FAIL" -gt 0 ]; then
  echo -e "${RED}失败用例：${RESET}"
  for c in "${FAILED_CASES[@]}"; do echo -e "  - $c"; done
  exit 1
fi
exit 0

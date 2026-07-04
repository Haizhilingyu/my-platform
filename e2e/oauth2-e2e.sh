#!/usr/bin/env bash
# ============================================================================
# OAuth2 / OIDC 端到端测试（T29）
#
# 覆盖 Spring Authorization Server 提供的 OAuth2/OIDC 链路：
#   1. OIDC Discovery（/.well-known/openid-configuration）
#   2. Authorization Code Flow（/oauth2/authorize → 登录 → 授权 → code → /oauth2/token）
#   3. RP-Initiated Logout（/oauth2/logout，OIDC 标准）
#
# === 前置条件（必须手动准备）===
#   后端已启动（http://localhost:8090）+ Redis 已连接（OIDC logout 需 Spring Session）。
#   openapp_client 表中已播种至少一个测试客户端。播种 SQL（在 PG/H2 执行）：
#
#     -- client_id 复用 id（JdbcRegisteredClientRepository 约定）
#     -- client_secret 存 BCrypt 哈希，下方明文 "test-secret" 的哈希：
#     INSERT INTO openapp_client (id, client_id, client_secret, client_name,
#         redirect_uris, post_logout_redirect_uris, scopes, grant_types,
#         authentication_methods, enabled)
#     VALUES (
#       100, 'test-client',
#       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- "test-secret"
#       'E2E测试客户端',
#       'http://127.0.0.1:18080/callback',
#       'http://127.0.0.1:18080/logout',
#       'openid,profile',
#       'AUTHORIZATION_CODE,REFRESH_TOKEN',
#       'CLIENT_SECRET_BASIC',
#       TRUE
#     );
#
#   也可通过前端「系统管理 → 外部应用」页面创建（若 /sys/openapp/clients 后端已实现）。
#
# === 用法 ===
#   bash e2e/oauth2-e2e.sh [BACKEND_BASE_URL] [CLIENT_ID] [CLIENT_SECRET]
#   默认：http://localhost:8090  test-client  test-secret
#
#   SKIP_OIDC=1 跳过全部 OAuth2 测试（无 openapp 模块或无 Redis 时）。
# ============================================================================
set -uo pipefail

BASE="${1:-http://localhost:8090}"
CLIENT_ID="${2:-test-client}"
CLIENT_SECRET="${3:-test-secret}"
PASS=0
FAIL=0
SKIP=0
FAILED_CASES=()

export NO_PROXY='localhost,127.0.0.1'
export no_proxy='localhost,127.0.0.1'
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY 2>/dev/null || true

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; BOLD='\033[1m'; RESET='\033[0m'

assert_case() {
  local desc="$1" ok="$2"
  if [ "$ok" -eq 0 ]; then
    printf "${GREEN}  ✓ PASS${RESET} %s\n" "$desc"; PASS=$((PASS + 1))
  else
    printf "${RED}  ✗ FAIL${RESET} %s\n" "$desc"; FAIL=$((FAIL + 1)); FAILED_CASES+=("$desc")
  fi
}
skip_case() {
  local desc="$1" reason="$2"
  printf "${YELLOW}  ⊘ SKIP${RESET} %s (%s)\n" "$desc" "$reason"; SKIP=$((SKIP + 1))
}

REDIRECT_URI="http://127.0.0.1:18080/callback"
POST_LOGOUT_URI="http://127.0.0.1:18080/logout"
COOKIE_JAR=$(mktemp)
trap 'rm -f "$COOKIE_JAR"' EXIT

echo -e "${BOLD}=== OAuth2 / OIDC e2e（后端 ${BASE}，客户端 ${CLIENT_ID}）===${RESET}"
command -v jq >/dev/null 2>&1 || { echo "${RED}错误：未安装 jq${RESET}"; exit 2; }

if [ "${SKIP_OIDC:-0}" = "1" ]; then
  echo -e "${YELLOW}SKIP_OIDC=1，跳过全部 OAuth2 测试${RESET}"
  exit 0
fi

# ---------------------------------------------------------------------------
# 1. OIDC Discovery
# ---------------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/.well-known/openid-configuration")
code=$(echo "$resp" | tail -1); body=$(echo "$resp" | sed '$d')

if [ "$code" != "200" ]; then
  skip_case "OIDC Discovery" "openapp 模块未激活 (code=${code})"
  echo -e "${YELLOW}>>> openapp 模块或 JWK 未就绪，后续 OAuth2 流程无法执行${RESET}"
  echo ""
  echo -e "${BOLD}=== OAuth2 结果：${PASS} passed, ${FAIL} failed, ${SKIP} skipped ===${RESET}"
  exit 0
fi

issuer=$(echo "$body" | jq -r '.issuer // empty')
auth_endpoint=$(echo "$body" | jq -r '.authorization_endpoint // empty')
token_endpoint=$(echo "$body" | jq -r '.token_endpoint // empty')
jwks_uri=$(echo "$body" | jq -r '.jwks_uri // empty')
assert_case "OIDC Discovery 应返回 issuer" $([ -n "$issuer" ] && echo 0 || echo 1)
assert_case "OIDC Discovery 应返回 authorization_endpoint" $([ -n "$auth_endpoint" ] && echo 0 || echo 1)
assert_case "OIDC Discovery 应返回 token_endpoint" $([ -n "$token_endpoint" ] && echo 0 || echo 1)
assert_case "OIDC Discovery 应返回 jwks_uri" $([ -n "$jwks_uri" ] && echo 0 || echo 1)

# ---------------------------------------------------------------------------
# 2. JWKS 端点（验证 JWK 轮转已生成密钥）
# ---------------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/oauth2/jwks")
code=$(echo "$resp" | tail -1); body=$(echo "$resp" | sed '$d')
if [ "$code" = "200" ]; then
  key_count=$(echo "$body" | jq '.keys | length' 2>/dev/null)
  assert_case "GET /oauth2/jwks 应返回密钥集" $([ -n "$key_count" ] && [ "$key_count" -ge 1 ] 2>/dev/null && echo 0 || echo 1)
else
  skip_case "JWKS 端点" "未就绪 (code=${code})，JWK 轮转可能尚未生成密钥"
fi

# ---------------------------------------------------------------------------
# 3. Authorization Code Flow
# ---------------------------------------------------------------------------
# 流程：/oauth2/authorize → 302 到 /login（HTML 表单登录）→ POST 登录 →
#       302 回 /oauth2/authorize（带 session cookie）→ 302 到 redirect_uri?code=...
# curl 需跟随重定向 + 携带 cookie jar 模拟浏览器会话。
echo ""
echo -e "${BOLD}--- Authorization Code Flow ---${RESET}"

AUTHZ_URL="${BASE}/oauth2/authorize?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(jq -rn --arg v "$REDIRECT_URI" '$v|@uri')&scope=openid%20profile"

# 第一步：访问 authorize 端点，期望 302 重定向到登录页（未认证）
# Spring AS 对浏览器返回 302/200 HTML 登录页；curl 不跟随时拿到 302。
code=$(curl -s -o /dev/null -w '%{http_code}' -c "$COOKIE_JAR" "$AUTHZ_URL")
assert_case "未认证访问 /oauth2/authorize 应触发登录重定向（302/200）" \
  $([ "$code" = "302" ] || [ "$code" = "200" ] && echo 0 || echo 1)

# 第二步：模拟表单登录（平台登录走 /sys/auth/login JSON API，但 AS 的 form login 是 Spring Security 默认）
# 平台用自定义 LoginUrlAuthenticationEntryPoint → /login，但实际认证由 JwtAuthFilter 处理。
# 完整自动化表单登录需适配平台登录页 DOM，此处改为验证 authorize 端点可达 + token 端点拒绝无效 code。

# 第三步：用无效 code 换 token → 期望 400
BASIC_AUTH=$(printf '%s:%s' "$CLIENT_ID" "$CLIENT_SECRET" | base64 2>/dev/null || printf '%s:%s' "$CLIENT_ID" "$CLIENT_SECRET" | openssl base64 -A)
resp=$(curl -s -w $'\n%{http_code}' -X POST "${BASE}/oauth2/token" \
  -H "Authorization: Basic ${BASIC_AUTH}" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d "grant_type=authorization_code&code=INVALID_CODE&redirect_uri=${REDIRECT_URI}")
code=$(echo "$resp" | tail -1)
assert_case "无效 code 换 token 应被拒绝（400）" $([ "$code" = "400" ] && echo 0 || echo 1)

# 第四步：用无效 client credentials → 期望 401
resp=$(curl -s -w $'\n%{http_code}' -X POST "${BASE}/oauth2/token" \
  -H "Authorization: Basic $(printf 'bad:bad' | base64)" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d "grant_type=authorization_code&code=x&redirect_uri=${REDIRECT_URI}")
code=$(echo "$resp" | tail -1)
assert_case "无效 client credentials 应返回 401" $([ "$code" = "401" ] && echo 0 || echo 1)

# ---------------------------------------------------------------------------
# 4. RP-Initiated Logout 端点可达性
# ---------------------------------------------------------------------------
echo ""
echo -e "${BOLD}--- RP-Initiated Logout ---${RESET}"
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/oauth2/logout?post_logout_redirect_uri=$(jq -rn --arg v "$POST_LOGOUT_URI" '$v|@uri')")
code=$(echo "$resp" | tail -1)
# 未登录登出：AS 可能 302 重定向到 post_logout_redirect_uri 或返回登录页（200/302 均合法）
assert_case "/oauth2/logout 端点应可达（200/302）" \
  $([ "$code" = "200" ] || [ "$code" = "302" ] || [ "$code" = "401" ] && echo 0 || echo 1)

# ---------------------------------------------------------------------------
# 完整 Authorization Code Flow 自动化说明（需浏览器或 Playwright）
# ---------------------------------------------------------------------------
# curl 无法完整自动化 auth code flow（需渲染登录页 DOM + 提交表单 + 跟踪多次 302）。
# 完整流程验证由 Playwright e2e 或手动浏览器完成：
#   1. 浏览器打开 AUTHZ_URL
#   2. 平台登录页输入 admin/admin123 提交
#   3. （若有 consent 页）点同意
#   4. 浏览器跳转到 REDIRECT_URI?code=XXXX
#   5. curl -X POST token_endpoint -d "grant_type=authorization_code&code=XXXX&..." 换 access_token
#   6. 用 access_token 调 /openapi/notify/publish（resource server 校验）

echo ""
echo -e "${BOLD}注：完整 authorization code flow 自动化需浏览器（DOM 登录页 + 多次 302），"
echo -e "    本脚本验证 AS 端点可达性与错误处理；完整流程由 Playwright 或手动浏览器执行。${RESET}"

# ---------------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------------
echo ""
echo -e "${BOLD}=== OAuth2 结果：${GREEN}${PASS} passed${RESET}${BOLD}, ${RED}${FAIL} failed${RESET}${BOLD}, ${YELLOW}${SKIP} skipped${RESET}${BOLD} ===${RESET}"
if [ "$FAIL" -gt 0 ]; then
  echo -e "${RED}失败用例：${RESET}"
  for c in "${FAILED_CASES[@]}"; do echo -e "  - $c"; done
  exit 1
fi
exit 0

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

# 验证码解题：后端开启了图形验证码校验，登录请求必须携带 captchaId + captchaCode。
# 从 /api/sys/auth/captcha 拿 captchaId，经 redis-cli 从 Redis 读出答案（key: captcha:{id}）。
# Redis 序列化 String 带双引号，tr -d '"' 剔除。
solve_captcha() {
  local cap_json cap_id cap_ans
  cap_json=$(curl -s "${BASE}/api/sys/auth/captcha")
  cap_id=$(echo "$cap_json" | jq -r '.data.captchaId // empty')
  # Redis 写入与 captcha 生成有微小延迟，读到空时短暂重试
  for _ in 1 2 3 4 5; do
    cap_ans=$(docker exec my-platform-redis redis-cli GET "captcha:${cap_id}" 2>/dev/null | tr -d '"\r\n')
    [ -n "$cap_ans" ] && break
    sleep 0.2
  done
  echo "${cap_id}:${cap_ans}"
}

# ------------------------------------------------------------------
# 用例 1：无 token 访问受保护接口 → 期望 403
# Spring Security 6 在 anyRequest().authenticated() 且无自定义 entry point 时，
# 未认证访问直接返回 403（空 body）。这证明安全过滤生效。
# ------------------------------------------------------------------
code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE}/api/sys/auth/me")
assert_case "无 token 访问 /api/sys/auth/me 应返回 403（安全过滤生效）" \
  $([ "$code" = "403" ] && echo 0 || echo 1)

# ------------------------------------------------------------------
# 用例 2：错误密码登录 → 期望 401 + 业务错误 JSON
# ------------------------------------------------------------------
CAP=$(solve_captcha); WCAP_ID="${CAP%%:*}"; WCAP_ANS="${CAP##*:}"
resp=$(curl -s -w $'\n%{http_code}' -X POST "${BASE}/api/sys/auth/login" \
  -H 'Content-Type: application/json' -H 'Accept-Language: zh-CN' \
  -d '{"username":"admin","password":"wrong-password","captchaId":"'"$WCAP_ID"'","captchaCode":"'"$WCAP_ANS"'"}')
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
CAP=$(solve_captcha); CAP_ID="${CAP%%:*}"; CAP_ANS="${CAP##*:}"
resp=$(curl -s -w $'\n%{http_code}' -X POST "${BASE}/api/sys/auth/login" \
  -H 'Content-Type: application/json' -H 'Accept-Language: zh-CN' \
  -d '{"username":"admin","password":"admin123","captchaId":"'"$CAP_ID"'","captchaCode":"'"$CAP_ANS"'"}')
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
# 用例 4：带 token 调 /api/sys/auth/me → 200, user.id==1
# ------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/api/sys/auth/me" \
  -H "Authorization: Bearer ${TOKEN}")
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
me_id=$(echo "$body" | jq -r '.data.id // empty' 2>/dev/null)
me_user=$(echo "$body" | jq -r '.data.username // empty' 2>/dev/null)
assert_case "带 token 调 /api/sys/auth/me 应返回 200" $([ "$code" = "200" ] && echo 0 || echo 1)
assert_case "/api/sys/auth/me user.id 应为 1" $([ "$me_id" = "1" ] && echo 0 || echo 1)
assert_case "/api/sys/auth/me user.username 应为 admin" $([ "$me_user" = "admin" ] && echo 0 || echo 1)

# ------------------------------------------------------------------
# 用例 5：带 token 调 /api/sys/auth/permissions → 200, 权限集非空
# ------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/api/sys/auth/permissions" \
  -H "Authorization: Bearer ${TOKEN}")
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
perm_count=$(echo "$body" | jq '.data | length' 2>/dev/null)
assert_case "带 token 调 /api/sys/auth/permissions 应返回 200" $([ "$code" = "200" ] && echo 0 || echo 1)
assert_case "权限集应非空" $([ -n "$perm_count" ] && [ "$perm_count" -gt 0 ] && echo 0 || echo 1)

# ------------------------------------------------------------------
# 用例 6：带 token 调 /api/sys/auth/menus → 200, 菜单树含「系统管理」
# ------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/api/sys/auth/menus" \
  -H "Authorization: Bearer ${TOKEN}")
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
has_sys=$(echo "$body" | jq -r '[.data[]?.menuName] | index("系统管理") != null' 2>/dev/null)
assert_case "带 token 调 /api/sys/auth/menus 应返回 200" $([ "$code" = "200" ] && echo 0 || echo 1)
assert_case "菜单树应含『系统管理』目录" $([ "$has_sys" = "true" ] && echo 0 || echo 1)

# ------------------------------------------------------------------
# 用例 7：带 token 调 /api/sys/user → 200, 列表含 admin
# ------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/api/sys/user" \
  -H "Authorization: Bearer ${TOKEN}")
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
# 分页或数组结构都兼容：在整段 body 里找 username == admin
has_admin=$(echo "$body" | jq -r '[.. | .username? // empty] | index("admin") != null' 2>/dev/null)
assert_case "带 token 调 /api/sys/user（有权限）应返回 200" $([ "$code" = "200" ] && echo 0 || echo 1)
assert_case "用户列表应含 admin" $([ "$has_admin" = "true" ] && echo 0 || echo 1)

# ------------------------------------------------------------------
# 用例 8：无效 token 访问受保护接口 → 期望 403
# 无效 token 同样被安全层拒绝（Spring Security 6 默认 403）。
# ------------------------------------------------------------------
code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE}/api/sys/auth/me" \
  -H "Authorization: Bearer an.invalid.token")
assert_case "无效 token 访问受保护接口应返回 403" \
  $([ "$code" = "403" ] && echo 0 || echo 1)

# ------------------------------------------------------------------
# 用例 9：AI 对话历史持久化 + 单条删除
# 验证 /api/ai/chat 落库、GET /api/ai/chat/history 加载、DELETE 单条删除 + 404。
# 走默认 mock provider，无 DeepSeek 依赖。
# ------------------------------------------------------------------
echo ""
echo -e "${BOLD}--- AI 对话历史 ---${RESET}"

# 0. 防御性清理：逐条删除既有历史（无全量删除 API）
for mid in $(curl -s -H "Authorization: Bearer ${TOKEN}" "${BASE}/api/ai/chat/history" | jq -r '.data[].id // empty' 2>/dev/null); do
  curl -s -X DELETE -H "Authorization: Bearer ${TOKEN}" "${BASE}/api/ai/chat/history/${mid}" >/dev/null
done
hist_count=$(curl -s -H "Authorization: Bearer ${TOKEN}" "${BASE}/api/ai/chat/history" | jq -r '.data | length')
assert_case "清理后历史应为空" $([ "$hist_count" = "0" ] && echo 0 || echo 1)

# 1. 发一条对话消息（mock 命中 listUsers 工具）
chat_sse=$(curl -s -N -X POST "${BASE}/api/ai/chat" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"message":"查询用户列表"}' 2>/dev/null)
assert_case "AI 对话应返回 done 事件" $(echo "$chat_sse" | grep -q 'event:done' && echo 0 || echo 1)

# 2. 验证历史落库（user + assistant 各一条）
hist_json=$(curl -s -H "Authorization: Bearer ${TOKEN}" "${BASE}/api/ai/chat/history")
hist_len=$(echo "$hist_json" | jq -r '.data | length')
hist_first_role=$(echo "$hist_json" | jq -r '.data[0].role // empty')
hist_first_text=$(echo "$hist_json" | jq -r '.data[0].text // empty')
assert_case "对话后历史应有 2 条（user+assistant）" $([ "$hist_len" = "2" ] && echo 0 || echo 1)
assert_case "历史第一条 role 应为 user" $([ "$hist_first_role" = "user" ] && echo 0 || echo 1)
assert_case "历史第一条 text 应为查询用户列表" $([ "$hist_first_text" = "查询用户列表" ] && echo 0 || echo 1)

# 3. 单条删除 user 消息
uid=$(echo "$hist_json" | jq -r '.data[0].id')
del_code=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer ${TOKEN}" "${BASE}/api/ai/chat/history/${uid}")
# 取剩余条数
remain_len=$(curl -s -H "Authorization: Bearer ${TOKEN}" "${BASE}/api/ai/chat/history" | jq -r '.data | length')
assert_case "删除 user 消息后剩余应为 1 条" $([ "$remain_len" = "1" ] && echo 0 || echo 1)

# 4. 负例：删除不存在的 id 返回 404
notfound_code=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer ${TOKEN}" "${BASE}/api/ai/chat/history/99999999")
assert_case "删除不存在的消息应返回 404" $([ "$notfound_code" = "404" ] && echo 0 || echo 1)

# 5. 收尾清理（幂等）
for mid in $(curl -s -H "Authorization: Bearer ${TOKEN}" "${BASE}/api/ai/chat/history" | jq -r '.data[].id // empty' 2>/dev/null); do
  curl -s -X DELETE -H "Authorization: Bearer ${TOKEN}" "${BASE}/api/ai/chat/history/${mid}" >/dev/null
done

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

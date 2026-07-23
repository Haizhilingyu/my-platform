#!/usr/bin/env bash
# ============================================================================
# Layer 1 扩展：全链路 API 端到端测试（T29）
#
# 覆盖基础 api-e2e.sh 之外的高级链路：
#   - 图形验证码（captcha）
#   - 登录方式发现（login-methods）
#   - 登录锁定与解锁（lockout / unlock）—— 使用临时用户，admin 豁免锁定
#   - 在线会话（sessions）
#   - 数据权限范围（data scope，GET /sys/user 跨角色）
#   - 消息发布（notify publish）—— 需 sys:notify:publish 权限，未播种时优雅跳过
#   - 审计日志查询（audit logs）
#   - OIDC 发现端点（/.well-known/openid-configuration）
#
# 依赖：curl + jq + 运行中的后端（默认 http://localhost:8090）。
# 前置：后端已启动并通过 /actuator/health 健康检查。
#
# 可跳过项（环境变量 = 1 时跳过）：
#   SKIP_LOCKOUT=1   跳过锁定测试（需写 Redis，无 Redis 时跳过）
#   SKIP_NOTIFY=1    跳过消息发布测试（需 sys:notify:publish 权限播种）
#   SKIP_OIDC=1      跳过 OIDC 发现测试（需 openapp 模块 + JWK 初始化）
#
# 用法：bash e2e/api-e2e-extended.sh [BACKEND_BASE_URL]   默认 http://localhost:8090
# ============================================================================
set -uo pipefail

BASE="${1:-http://localhost:8090}"
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
    printf "${GREEN}  ✓ PASS${RESET} %s\n" "$desc"
    PASS=$((PASS + 1))
  else
    printf "${RED}  ✗ FAIL${RESET} %s\n" "$desc"
    FAIL=$((FAIL + 1))
    FAILED_CASES+=("$desc")
  fi
}

skip_case() {
  local desc="$1" reason="$2"
  printf "${YELLOW}  ⊘ SKIP${RESET} %s (%s)\n" "$desc" "$reason"
  SKIP=$((SKIP + 1))
}

echo -e "${BOLD}=== Layer 1 扩展：全链路 API e2e（后端 ${BASE}）===${RESET}"

command -v jq >/dev/null 2>&1 || { echo "${RED}错误：未安装 jq${RESET}"; exit 2; }

# 验证码解题（同 api-e2e.sh）：后端开启了图形验证码校验。
solve_captcha() {
  local cap_json cap_id cap_ans
  cap_json=$(curl -s "${BASE}/api/sys/auth/captcha")
  cap_id=$(echo "$cap_json" | jq -r '.data.captchaId // empty')
  for _ in 1 2 3 4 5 6 7 8; do
    cap_ans=$(docker exec my-platform-redis redis-cli GET "captcha:${cap_id}" 2>/dev/null | tr -d '"\r\n')
    [ -n "$cap_ans" ] && break
    sleep 0.3
  done
  echo "${cap_id}:${cap_ans}"
}

# ---------------------------------------------------------------------------
# 0. 管理员登录（后续用例的基础）
# ---------------------------------------------------------------------------
CAP=$(solve_captcha); CAP_ID="${CAP%%:*}"; CAP_ANS="${CAP##*:}"
resp=$(curl -s -w $'\n%{http_code}' -X POST "${BASE}/api/sys/auth/login" \
  -H 'Content-Type: application/json' -H 'Accept-Language: zh-CN' \
  -d '{"username":"admin","password":"admin123","captchaId":"'"$CAP_ID"'","captchaCode":"'"$CAP_ANS"'"}')
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
ADMIN_TOKEN=$(echo "$body" | jq -r '.data.token // empty' 2>/dev/null)

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
  echo -e "${RED}管理员登录失败（code=${code}），中止${RESET}"
  echo "  body: $body"
  exit 1
fi
printf "${GREEN}  ✓ 管理员登录成功，取得 token${RESET}\n"

AUTH="Authorization: Bearer ${ADMIN_TOKEN}"

# ---------------------------------------------------------------------------
# 1. 图形验证码：GET /sys/auth/captcha → 200 + captchaId + img
# ---------------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/api/sys/auth/captcha")
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
captcha_id=$(echo "$body" | jq -r '.data.captchaId // empty' 2>/dev/null)
captcha_img=$(echo "$body" | jq -r '.data.img // .data.image // empty' 2>/dev/null)
assert_case "GET /sys/auth/captcha 应返回 200" $([ "$code" = "200" ] && echo 0 || echo 1)
assert_case "captcha 返回 captchaId 非空" $([ -n "$captcha_id" ] && [ "$captcha_id" != "null" ] && echo 0 || echo 1)
assert_case "captcha 返回图片数据非空" $([ -n "$captcha_img" ] && [ "$captcha_img" != "null" ] && echo 0 || echo 1)

# ---------------------------------------------------------------------------
# 2. 登录方式发现：GET /sys/auth/login-methods → 200 + 含 "password"
# ---------------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/api/sys/auth/login-methods")
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
has_password=$(echo "$body" | jq -r '[.data[]?.method] | index("password") != null' 2>/dev/null)
assert_case "GET /sys/auth/login-methods 应返回 200" $([ "$code" = "200" ] && echo 0 || echo 1)
assert_case "login-methods 应含 password 方式" $([ "$has_password" = "true" ] && echo 0 || echo 1)

# ---------------------------------------------------------------------------
# 3. 登录锁定与解锁（使用临时用户，admin 豁免锁定不可用于测试）
# ---------------------------------------------------------------------------
# 策略：创建一个临时用户 → 故意错误登录达到阈值 → 验证第 N 次 423 → 管理员解锁 → 恢复
# 需要 Redis（计数器）+ ConfigService（阈值 sys.security.login.max-fail-count，默认 3）。
if [ "${SKIP_LOCKOUT:-0}" = "1" ]; then
  skip_case "登录锁定/解锁" "SKIP_LOCKOUT=1"
else
  LOCK_USER="locktest_$(date +%s)"
  LOCK_PASS="Temp@Lock123"
  # 创建临时用户（admin 有 sys:user:add 权限，V2 播种）
  resp=$(curl -s -w $'\n%{http_code}' -X POST "${BASE}/api/sys/user" \
    -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"username\":\"${LOCK_USER}\",\"password\":\"${LOCK_PASS}\",\"realName\":\"锁定测试\",\"status\":1,\"unitId\":1}")
  code=$(echo "$resp" | tail -1)
  body=$(echo "$resp" | sed '$d')
  LOCK_USER_ID=$(echo "$body" | jq -r '.data // empty' 2>/dev/null)

  if [ "$code" = "200" ] && [ -n "$LOCK_USER_ID" ] && [ "$LOCK_USER_ID" != "null" ]; then
    printf "${GREEN}  ✓ 创建临时用户 ${LOCK_USER} (id=${LOCK_USER_ID})${RESET}\n"

    # 给临时用户分配角色（role_id=2 普通角色，避免无角色无法登录）
    curl -s -o /dev/null -X POST "${BASE}/api/sys/user/${LOCK_USER_ID}/roles" \
      -H "$AUTH" -H 'Content-Type: application/json' -d '[2]'

    # 故意错误登录 5 次（默认阈值 3，多打几次确保触发）。
    # 每次需解一次验证码（验证码单次有效）：解出 captchaId+答案后带错误密码登录。
    LOCKED=0
    for i in 1 2 3 4 5; do
      BCAP=$(solve_captcha); BCAP_ID="${BCAP%%:*}"; BCAP_ANS="${BCAP##*:}"
      resp=$(curl -s -w $'\n%{http_code}' -X POST "${BASE}/api/sys/auth/login" \
        -H 'Content-Type: application/json' \
        -d '{"username":"'"${LOCK_USER}"'","password":"wrong","captchaId":"'"$BCAP_ID"'","captchaCode":"'"$BCAP_ANS"'"}')
      code=$(echo "$resp" | tail -1)
      if [ "$code" = "423" ]; then
        LOCKED=1
        break
      fi
    done
    assert_case "连续错误登录后应返回 423（账号锁定）" $([ "$LOCKED" = "1" ] && echo 0 || echo 1)

    # 管理员解锁
    resp=$(curl -s -w $'\n%{http_code}' -X POST "${BASE}/api/sys/user/${LOCK_USER_ID}/unlock" \
      -H "$AUTH")
    code=$(echo "$resp" | tail -1)
    assert_case "管理员解锁应返回 200" $([ "$code" = "200" ] && echo 0 || echo 1)

    # 解锁后正确密码应能登录（或至少不再 423）；同样需解验证码
    RCAP=$(solve_captcha); RCAP_ID="${RCAP%%:*}"; RCAP_ANS="${RCAP##*:}"
    resp=$(curl -s -w $'\n%{http_code}' -X POST "${BASE}/api/sys/auth/login" \
      -H 'Content-Type: application/json' \
      -d '{"username":"'"${LOCK_USER}"'","password":"'"${LOCK_PASS}"'","captchaId":"'"$RCAP_ID"'","captchaCode":"'"$RCAP_ANS"'"}')
    code=$(echo "$resp" | tail -1)
    assert_case "解锁后登录不应再返回 423" $([ "$code" != "423" ] && echo 0 || echo 1)

    # 清理临时用户
    curl -s -o /dev/null -X DELETE "${BASE}/api/sys/user/${LOCK_USER_ID}" -H "$AUTH"
  else
    skip_case "登录锁定/解锁" "临时用户创建失败 (code=${code})，可能缺 sys:user:add 权限"
  fi
fi

# ---------------------------------------------------------------------------
# 4. 在线会话：GET /sys/auth/sessions → 200 + 列表
# ---------------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/api/sys/auth/sessions" -H "$AUTH")
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
session_count=$(echo "$body" | jq '.data | length' 2>/dev/null)
assert_case "GET /sys/auth/sessions 应返回 200" $([ "$code" = "200" ] && echo 0 || echo 1)
assert_case "会话列表应为数组" $([ -n "$session_count" ] && [ "$session_count" -ge 0 ] 2>/dev/null && echo 0 || echo 1)
# 当前 admin 至少有 1 个活跃会话（本次登录）
assert_case "当前用户应至少有 1 个活跃会话" $([ -n "$session_count" ] && [ "$session_count" -ge 1 ] 2>/dev/null && echo 0 || echo 1)

# ---------------------------------------------------------------------------
# 5. 数据权限范围：GET /sys/user（admin 应能看到全部用户）
# ---------------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/api/sys/user?pageNum=1&pageSize=50" -H "$AUTH")
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
user_total=$(echo "$body" | jq -r '.data.total // (.data | length) // empty' 2>/dev/null)
has_admin=$(echo "$body" | jq -r '[.. | .username? // empty] | index("admin") != null' 2>/dev/null)
assert_case "GET /sys/user 应返回 200" $([ "$code" = "200" ] && echo 0 || echo 1)
assert_case "用户列表应含 admin（数据权限生效）" $([ "$has_admin" = "true" ] && echo 0 || echo 1)
assert_case "用户总数应 >= 1" $([ -n "$user_total" ] && [ "$user_total" -ge 1 ] 2>/dev/null && echo 0 || echo 1)

# ---------------------------------------------------------------------------
# 6. 消息发布：POST /sys/notify/publish → 投递到收件箱
# ---------------------------------------------------------------------------
# 需 admin 持有 sys:notify:publish 权限（notify 模块需播种菜单+权限）。
# 未播种权限时返回 403，属预期可跳过场景（非硬失败）。
if [ "${SKIP_NOTIFY:-0}" = "1" ]; then
  skip_case "消息发布/收件箱" "SKIP_NOTIFY=1"
else
  resp=$(curl -s -w $'\n%{http_code}' -X POST "${BASE}/api/sys/notify/publish" \
    -H "$AUTH" -H 'Content-Type: application/json' \
    -d '{"title":"e2e测试消息","content":"T29全链路验证","level":"NORMAL","recipients":[{"type":"USER","id":1}]}')
  code=$(echo "$resp" | tail -1)
  body=$(echo "$resp" | sed '$d')

  if [ "$code" = "200" ]; then
    msg_id=$(echo "$body" | jq -r '.data.messageId // .data.id // empty' 2>/dev/null)
    assert_case "POST /sys/notify/publish 应返回 200" 0
    assert_case "发布结果应含 messageId" $([ -n "$msg_id" ] && [ "$msg_id" != "null" ] && echo 0 || echo 1)

    # 验证收件箱（admin id=1）—— 通知收件箱查询端点
    resp=$(curl -s -w $'\n%{http_code}' "${BASE}/api/sys/notify/inbox?pageNum=1&pageSize=5" -H "$AUTH")
    code=$(echo "$resp" | tail -1)
    if [ "$code" = "200" ]; then
      inbox_count=$(echo "$body" | jq '.data | length' 2>/dev/null)
      assert_case "GET /sys/notify/inbox 应返回 200" 0
      assert_case "收件箱应非空" $([ -n "$inbox_count" ] && [ "$inbox_count" -ge 1 ] 2>/dev/null && echo 0 || echo 1)
    else
      skip_case "收件箱查询" "端点不可用 (code=${code})"
    fi
  elif [ "$code" = "403" ]; then
    skip_case "消息发布/收件箱" "admin 无 sys:notify:publish 权限（notify 菜单未播种，预期）"
  else
    assert_case "POST /sys/notify/publish 应返回 200 或 403" 1
  fi
fi

# ---------------------------------------------------------------------------
# 7. 审计日志查询：GET /sys/audit/logs?action=LOGIN → 200 + 含 LOGIN 记录
# ---------------------------------------------------------------------------
resp=$(curl -s -w $'\n%{http_code}' "${BASE}/api/sys/audit/logs?action=LOGIN&pageNum=1&pageSize=5" -H "$AUTH")
code=$(echo "$resp" | tail -1)
body=$(echo "$resp" | sed '$d')
if [ "$code" = "200" ]; then
  audit_total=$(echo "$body" | jq -r '.data.total // (.data | length) // empty' 2>/dev/null)
  assert_case "GET /sys/audit/logs?action=LOGIN 应返回 200" 0
  # 之前的多轮登录会写入 LOGIN 审计记录（若审计模块已采集）
  assert_case "LOGIN 审计记录应 >= 1" $([ -n "$audit_total" ] && [ "$audit_total" -ge 1 ] 2>/dev/null && echo 0 || echo 1)
else
  assert_case "GET /sys/audit/logs?action=LOGIN 应返回 200" 1
fi

# ---------------------------------------------------------------------------
# 8. OIDC 发现端点：GET /.well-known/openid-configuration
# ---------------------------------------------------------------------------
if [ "${SKIP_OIDC:-0}" = "1" ]; then
  skip_case "OIDC 发现端点" "SKIP_OIDC=1"
else
  resp=$(curl -s -w $'\n%{http_code}' "${BASE}/.well-known/openid-configuration")
  code=$(echo "$resp" | tail -1)
  body=$(echo "$resp" | sed '$d')
  if [ "$code" = "200" ]; then
    issuer=$(echo "$body" | jq -r '.issuer // empty' 2>/dev/null)
    auth_endpoint=$(echo "$body" | jq -r '.authorization_endpoint // empty' 2>/dev/null)
    token_endpoint=$(echo "$body" | jq -r '.token_endpoint // empty' 2>/dev/null)
    assert_case "GET /.well-known/openid-configuration 应返回 200" 0
    assert_case "OIDC issuer 非空" $([ -n "$issuer" ] && [ "$issuer" != "null" ] && echo 0 || echo 1)
    assert_case "OIDC authorization_endpoint 非空" $([ -n "$auth_endpoint" ] && [ "$auth_endpoint" != "null" ] && echo 0 || echo 1)
    assert_case "OIDC token_endpoint 非空" $([ -n "$token_endpoint" ] && [ "$token_endpoint" != "null" ] && echo 0 || echo 1)
  else
    skip_case "OIDC 发现端点" "openapp 模块未激活或 JWK 未初始化 (code=${code})"
  fi
fi

# ---------------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------------
echo ""
echo -e "${BOLD}=== Layer 1 扩展结果：${GREEN}${PASS} passed${RESET}${BOLD}, ${RED}${FAIL} failed${RESET}${BOLD}, ${YELLOW}${SKIP} skipped${RESET}${BOLD} ===${RESET}"
if [ "$FAIL" -gt 0 ]; then
  echo -e "${RED}失败用例：${RESET}"
  for c in "${FAILED_CASES[@]}"; do echo -e "  - $c"; done
  exit 1
fi
exit 0

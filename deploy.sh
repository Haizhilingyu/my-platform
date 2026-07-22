#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# my-platform 远程部署脚本
#
# 用法: bash deploy.sh
#
# 流程: 打包源码 → 传输 → 构建 Docker 镜像 → 重启 app 容器
#
# 首次运行会自动配置 Caddy 反向代理。
# 后续运行只更新代码 + 重建镜像 + 重启 app。
# ============================================================

SERVER="root@107.175.124.191"
REMOTE_DIR="/root/my-platform"
IMAGE_NAME="my-platform:latest"
CADDYFILE="/root/kasm-browser/Caddyfile"
DOMAIN="develop.18408216834.site"
APP_CONTAINER="my-platform-app"

# 项目根目录（脚本所在目录）
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "📦 [1/4] 打包源码..."
tar czf /tmp/my-platform-src.tar.gz \
  --exclude='.git' --exclude='node_modules' --exclude='target' \
  --exclude='.idea' --exclude='dist' --exclude='__pycache__' \
  --exclude='*.o' --exclude='*.a' --exclude='.DS_Store' \
  --exclude='test-results' --exclude='logs' \
  --exclude='.playwright-mcp' --exclude='.sisyphus' \
  --exclude='coverage' \
  -C "$PROJECT_ROOT" .
echo "   $(du -h /tmp/my-platform-src.tar.gz | cut -f1) compressed"

echo "📤 [2/4] 传输到服务器..."
scp -o StrictHostKeyChecking=no /tmp/my-platform-src.tar.gz "$SERVER:/tmp/"

echo "🔨 [3/4] 构建镜像 + 部署..."
ssh -o StrictHostKeyChecking=no "$SERVER" bash << REMOTE_SCRIPT
set -euo pipefail
cd $REMOTE_DIR

# 解压最新源码（docker-compose.yml 在根目录，不在 tar 包内，不会覆盖）
tar xzf /tmp/my-platform-src.tar.gz 2>/dev/null || true

# 使用 Maven Central（服务器在美国，阿里云镜像反而慢）
cat > docker/maven-settings.xml << 'MSETTINGS'
<?xml version="1.0" encoding="UTF-8"?>
<settings>
</settings>
MSETTINGS

# 构建镜像
echo "   Building Docker image (no-cache)..."
docker build --no-cache -t $IMAGE_NAME -f docker/Dockerfile .

# Flyway 从多版本合并为单一 V1，需重建数据库（开发阶段不持久化数据）
echo "   Rebuilding database (Flyway V1 consolidation)..."
docker compose down -v 2>/dev/null || true
docker volume rm ${REMOTE_DIR##*/}_pgdata 2>/dev/null || true

# 全量启动（含 PG/Redis/App），全新数据库执行 V1 初始化
echo "   Starting full stack..."
docker compose up -d --remove-orphans

# 确保 Caddy 配置包含域名（首次部署时自动添加）
if ! grep -q "$DOMAIN" $CADDYFILE; then
  echo "" >> $CADDYFILE
  echo "$DOMAIN {" >> $CADDYFILE
  echo "    reverse_proxy $APP_CONTAINER:8090" >> $CADDYFILE
  echo "}" >> $CADDYFILE
  docker exec caddy caddy reload --config /etc/caddy/Caddyfile
  echo "   Caddy config added"
fi

# 清理悬空镜像
docker image prune -f 2>/dev/null || true
REMOTE_SCRIPT

echo "✅ [4/4] 部署完成!"
echo ""
# 访问端口用 8443（Cloudflare 支持的 HTTPS 端口，Caddyfile 中该域名仅配 :80 与 :8443；标准 :443 会 520）
echo "   URL:   https://$DOMAIN:8443"
echo "   状态:  curl -s https://$DOMAIN:8443/actuator/health"
echo "   日志:  ssh $SERVER docker logs $APP_CONTAINER -f"
echo "   重启:  ssh $SERVER docker restart $APP_CONTAINER"

rm -f /tmp/my-platform-src.tar.gz

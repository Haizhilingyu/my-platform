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
echo "   Building Docker image..."
docker build -t $IMAGE_NAME -f docker/Dockerfile .

# 重启 app 容器（PG/Redis 保持运行）
echo "   Restarting app container..."
docker compose up -d --force-recreate --no-deps app

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
echo "   URL:   https://$DOMAIN"
echo "   状态:  curl -s https://$DOMAIN/actuator/health"
echo "   日志:  ssh $SERVER docker logs $APP_CONTAINER -f"
echo "   重启:  ssh $SERVER docker restart $APP_CONTAINER"

rm -f /tmp/my-platform-src.tar.gz

#!/bin/bash
# 一键停服务 → 删旧镜像 → 重新构建并启动
set -e

cd "$(dirname "$0")"

echo "🛑 停止所有服务..."
docker compose down

echo "🗑️  删除旧镜像..."
docker rmi project_pilot-app 2>/dev/null || true

echo "🔨 重新构建并启动..."
docker compose up -d --build

echo "⏳ 等待服务启动（30 秒）..."
sleep 30

echo "✅ 服务已启动！访问 http://localhost:8080"
echo "📋 实时日志如下（Ctrl+C 退出）："
echo "=========================================="
docker logs -f pilot-app

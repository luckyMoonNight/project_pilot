#!/bin/bash
# 停止 project-pilot 所有服务
set -e

cd "$(dirname "$0")"

echo "🛑 停止所有服务..."
docker compose down

echo ""
echo "✅ 所有服务已停止"
echo ""
echo "💡 提示："
echo "  数据卷（MySQL、Chroma）已保留，下次启动数据不会丢失"
echo "  如需彻底清除数据：docker compose down -v"

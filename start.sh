#!/bin/bash
# 启动 project-pilot 所有服务
set -e

cd "$(dirname "$0")"

echo "🚀 启动所有服务..."
docker compose up -d

echo "⏳ 等待服务就绪..."

# 等待 MySQL 健康
echo -n "  MySQL: "
for i in $(seq 1 30); do
    if docker exec pilot-mysql mysqladmin ping -h localhost -uroot -proot --silent 2>/dev/null; then
        echo "✅"
        break
    fi
    echo -n "."
    sleep 2
done

# 等待 Chroma 就绪
echo -n "  Chroma: "
for i in $(seq 1 15); do
    if curl -s http://localhost:8000/api/v1/heartbeat > /dev/null 2>&1; then
        echo "✅"
        break
    fi
    echo -n "."
    sleep 2
done

# 等待 Java 应用就绪
echo -n "  App: "
for i in $(seq 1 30); do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1 || curl -s http://localhost:8080 > /dev/null 2>&1; then
        echo "✅"
        break
    fi
    echo -n "."
    sleep 3
done

echo ""
echo "=========================================="
echo "✅ 服务已启动！"
echo "  🌐 前端：http://localhost:8080"
echo "  🗄️  MySQL：localhost:3306"
echo "  🔍 Chroma：http://localhost:8000"
echo "  🐛 调试端口：localhost:5005"
echo "=========================================="
echo ""
echo "📋 查看日志：docker compose logs -f"
echo "📋 仅看 App：docker logs -f pilot-app"

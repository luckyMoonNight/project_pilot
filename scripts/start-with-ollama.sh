#!/usr/bin/env bash
# ============================================================
# project-pilot 一键启动脚本（本地 Ollama 方案）
#
# 功能：
#   1. 拉起 docker compose 全套服务（mysql / chroma / ollama / app）
#   2. 等待 ollama 就绪
#   3. 自动 pull chat 模型 + embedding 模型（已存在则跳过）
#   4. 简单做一次健康检查
#
# 用法：
#   bash scripts/start-with-ollama.sh
#   CHAT_MODEL=qwen2.5-coder:7b EMBED_MODEL=nomic-embed-text bash scripts/start-with-ollama.sh
# ============================================================

set -euo pipefail

CHAT_MODEL="${CHAT_MODEL:-qwen2.5:7b}"
EMBED_MODEL="${EMBED_MODEL:-bge-m3}"

cd "$(dirname "$0")/.."

echo "▶ [1/4] 启动 docker compose（mysql / chroma / ollama / app）..."
docker compose up -d

echo "▶ [2/4] 等待 Ollama 服务就绪..."
for i in {1..60}; do
  if curl -fsS http://localhost:11434/api/tags >/dev/null 2>&1; then
    echo "  ✔ Ollama 已就绪"
    break
  fi
  sleep 2
  if [ "$i" -eq 60 ]; then
    echo "  ✘ Ollama 60 秒内未就绪，请用 docker logs pilot-ollama 排查" >&2
    exit 1
  fi
done

pull_if_missing() {
  local model="$1"
  if docker exec pilot-ollama ollama list | awk '{print $1}' | grep -qx "$model"; then
    echo "  ✔ 模型 $model 已存在，跳过"
  else
    echo "  ▼ 拉取模型 $model（首次较慢，请耐心等待）..."
    docker exec pilot-ollama ollama pull "$model"
  fi
}

echo "▶ [3/4] 准备模型..."
pull_if_missing "$CHAT_MODEL"
pull_if_missing "$EMBED_MODEL"

echo "▶ [4/4] 健康检查..."
sleep 3
if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
  echo "  ✔ Java 服务存活"
elif curl -fsS http://localhost:8080/ >/dev/null 2>&1; then
  echo "  ✔ Java 服务存活（首页可访问）"
else
  echo "  ⚠ Java 服务暂未响应，可用 docker logs pilot-app 查看启动日志"
fi

cat <<EOF

================================================================
🎉  启动完成！

  前端体验：    http://localhost:8080/
  Ollama 控制：docker exec -it pilot-ollama ollama list
  查看日志：   docker logs -f pilot-app
  停止服务：   docker compose down
                （加 -v 会一起删数据卷，慎用）

  当前模型配置：
    Chat       = ${CHAT_MODEL}
    Embedding  = ${EMBED_MODEL}

  如果想换模型，重启时这样指定：
    CHAT_MODEL=qwen2.5-coder:7b EMBED_MODEL=nomic-embed-text bash scripts/start-with-ollama.sh
    （注意：换 embedding 模型后维度可能变化，需同步改 PILOT_VECTOR_DIMENSION）
================================================================
EOF

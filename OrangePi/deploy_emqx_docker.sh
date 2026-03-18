#!/bin/bash
echo "=== 切换至 Docker 部署 EMQX ==="

# 1. 彻底停止系统服务
echo "1. 禁用系统 EMQX 服务..."
sudo systemctl stop emqx
sudo systemctl disable emqx
sudo pkill -9 emqx
sudo pkill -9 beam.smp

# 2. 清理旧的 Docker 容器
echo "2. 清理旧容器..."
sudo docker stop emqx-mqtt 2>/dev/null
sudo docker rm emqx-mqtt 2>/dev/null

# 3. 启动 Docker 容器
# 使用环境变量直接配置：
# EMQX_ALLOW_ANONYMOUS=true (允许匿名)
# EMQX_LISTENERS__TCP__DEFAULT__BIND=0.0.0.0:1883 (监听所有接口)
echo "3. 启动新容器..."
sudo docker run -d \
    --name emqx-mqtt \
    --restart=always \
    --network host \
    -e EMQX_ALLOW_ANONYMOUS=true \
    -e EMQX_DASHBOARD__DEFAULT_USERNAME=admin \
    -e EMQX_DASHBOARD__DEFAULT_PASSWORD=public \
    emqx/emqx:latest

echo "等待容器启动 (15秒)..."
sleep 15

# 4. 检查状态
echo "=== 最终状态检查 ==="
if sudo docker ps | grep emqx-mqtt > /dev/null; then
    echo "✅ Docker EMQX 运行中!"
    echo "监听端口:"
    sudo netstat -tlnp | grep -E '1883|18083'
else
    echo "❌ 启动失败，查看日志:"
    sudo docker logs emqx-mqtt | tail -10
fi

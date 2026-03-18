#!/bin/bash
echo "=== Docker 救援模式 ==="

# 1. 彻底禁用损坏的系统服务
sudo systemctl stop emqx
sudo systemctl disable emqx
sudo pkill -9 emqx

# 2. 清理 Docker 环境
sudo docker stop emqx-mqtt 2>/dev/null
sudo docker rm emqx-mqtt 2>/dev/null

# 3. 启动 Docker
# 使用 host 网络模式，直接使用物理端口，避开端口映射问题
sudo docker run -d \
    --name emqx-mqtt \
    --restart=always \
    --net=host \
    -e EMQX_ALLOW_ANONYMOUS=true \
    -e EMQX_LISTENERS__TCP__DEFAULT__BIND=0.0.0.0:1883 \
    -e EMQX_DASHBOARD__LISTENERS__HTTP__BIND=0.0.0.0:18083 \
    emqx/emqx:latest

echo "等待启动..."
sleep 15
sudo docker logs emqx-mqtt | tail -5
sudo netstat -tlnp | grep 1883

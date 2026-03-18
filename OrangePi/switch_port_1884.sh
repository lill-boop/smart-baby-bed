#!/bin/bash
echo "=== 切换 EMQX 端口至 1884 ==="

# 1. 停止旧容器
sudo docker stop emqx-mqtt 2>/dev/null
sudo docker rm emqx-mqtt 2>/dev/null

# 2. 启动新容器 (端口映射 1884 -> 1883)
sudo docker run -d \
    --name emqx-mqtt \
    --restart=always \
    -p 1884:1883 \
    -p 18083:18083 \
    -e EMQX_ALLOW_ANONYMOUS=true \
    emqx/emqx:latest

# 3. 确保防火墙放行 1884
sudo iptables -I INPUT -p tcp --dport 1884 -j ACCEPT

echo "等待启动..."
sleep 10
sudo docker ps | grep emqx
sudo netstat -tlnp | grep 1884

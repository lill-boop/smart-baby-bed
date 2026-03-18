#!/bin/bash
echo "=== 切换 EMQX 端口至 8888 ==="

# 1. 停止旧容器
sudo docker stop emqx-mqtt 2>/dev/null
sudo docker rm emqx-mqtt 2>/dev/null

# 2. 启动新容器 (端口映射 8888 -> 1883)
# 注意：我们不用 host 网络模式了，改用 bridge 模式并做端口映射，这样更灵活
sudo docker run -d \
    --name emqx-mqtt \
    --restart=always \
    -p 8888:1883 \
    -p 18083:18083 \
    -e EMQX_ALLOW_ANONYMOUS=true \
    emqx/emqx:latest

# 3. 确保防火墙放行 8888
sudo iptables -I INPUT -p tcp --dport 8888 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 18083 -j ACCEPT

echo "等待启动..."
sleep 10
sudo docker ps | grep emqx
sudo netstat -tlnp | grep 8888

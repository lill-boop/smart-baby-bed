#!/bin/bash
echo "=== Host模式启动 EMQX (端口由配置文件决定: 1884) ==="

sudo docker stop emqx-mqtt 2>/dev/null
sudo docker rm emqx-mqtt 2>/dev/null

# 确保配置目录存在
mkdir -p /home/orangepi/emqx/etc

# 复制配置文件
cp /tmp/emqx.conf /home/orangepi/emqx/etc/emqx.conf

# 启动容器 (Host模式)
sudo docker run -d \
    --name emqx-mqtt \
    --restart=always \
    --net=host \
    -v /home/orangepi/emqx/etc/emqx.conf:/opt/emqx/etc/emqx.conf \
    -e EMQX_ALLOW_ANONYMOUS=true \
    emqx/emqx:latest

# 防火墙再次确保放行 1884
sudo iptables -I INPUT -p tcp --dport 1884 -j ACCEPT

echo "等待启动..."
sleep 10
sudo netstat -tlnp | grep 1884

#!/bin/bash
echo "=== 彻底清理防火墙 ==="

# 1. 禁用 UFW
echo "禁用 UFW..."
sudo ufw disable 

# 2. 清理 iptables 所有规则
echo "清理 iptables..."
sudo iptables -P INPUT ACCEPT
sudo iptables -P FORWARD ACCEPT
sudo iptables -P OUTPUT ACCEPT
sudo iptables -t nat -F
sudo iptables -t mangle -F
sudo iptables -F
sudo iptables -X

# 3. 检查端口
echo "当前监听端口:"
sudo netstat -tlnp | grep -E '1883|18083|8083|8883'

echo "所有防火墙已关闭，请重试连接！"

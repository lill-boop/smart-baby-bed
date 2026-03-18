#!/bin/bash
# 允许局域网访问 EMQX 1883 端口

echo "正在配置防火墙规则..."

# 检查 ufw 状态
if command -v ufw &> /dev/null; then
    echo "检测到 ufw 防火墙"
    sudo ufw allow from 192.168.0.0/24 to any port 1883
    sudo ufw allow from 192.168.10.0/24 to any port 1883
    sudo ufw reload
    echo "ufw 规则已添加"
fi

# 检查 iptables
if command -v iptables &> /dev/null; then
    echo "检查 iptables 规则..."
    sudo iptables -I INPUT -p tcp --dport 1883 -j ACCEPT
    sudo iptables -I INPUT -p tcp --dport 18083 -j ACCEPT
    echo "iptables 规则已添加"
fi

echo "防火墙配置完成！"
echo "测试 MQTT 端口："
netstat -tlnp | grep 1883

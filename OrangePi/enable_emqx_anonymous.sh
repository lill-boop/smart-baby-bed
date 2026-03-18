#!/bin/bash
# 配置 EMQX 认证

echo "正在配置 EMQX 认证..."

# 方案1: 允许匿名连接（最简单）
echo "添加匿名连接配置..."
echo '' | sudo tee -a /etc/emqx/emqx.conf
echo '# Allow anonymous MQTT connections' | sudo tee -a /etc/emqx/emqx.conf
echo 'mqtt.allow_anonymous = true' | sudo tee -a /etc/emqx/emqx.conf

# 重启 EMQX
echo "重启 EMQX 服务..."
sudo systemctl restart emqx

# 等待服务启动
sleep 5

# 检查状态
echo "检查 EMQX 状态..."
sudo systemctl status emqx --no-pager -l | head -10

echo "配置完成！"
echo "EMQX 现在允许匿名连接"

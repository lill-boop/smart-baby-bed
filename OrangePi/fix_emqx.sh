#!/bin/bash
echo "=== 开始修复 EMQX ==="

# 1. 停止所有冲突进程
echo "1. 停止旧进程..."
sudo docker stop emqx-mqtt 2>/dev/null
sudo docker rm emqx-mqtt 2>/dev/null
sudo systemctl stop emqx
sudo pkill -9 emqx
sudo pkill -9 beam.smp

# 2. 写入新配置 (EMQX 5.x HOCON 格式)
echo "2. 写入配置文件..."
sudo mv /etc/emqx/emqx.conf /etc/emqx/emqx.conf.bak.$(date +%s) 2>/dev/null

sudo cat > /etc/emqx/emqx.conf <<EOF
node {
  name = "emqx@127.0.0.1"
  cookie = "emqxsecretcookie"
  data_dir = "/var/lib/emqx"
}

cluster {
  discovery_strategy = manual
}

listeners.tcp.default {
  bind = "0.0.0.0:1883"
  max_connections = 1024000
}

mqtt {
  allow_anonymous = true
}

dashboard {
    listeners.http {
        bind = "0.0.0.0:18083"
    }
    default_username = "admin"
    default_password = "public"
}

log {
    console {
        enable = true
        level = warning
    }
}
EOF

# 3. 启动服务
echo "3. 启动服务..."
sudo systemctl start emqx

echo "等待启动 (10秒)..."
sleep 10

# 4. 检查状态
echo "=== 检查状态 ==="
if sudo systemctl is-active --quiet emqx; then
    echo "✅ EMQX 服务运行中"
    echo "端口状态:"
    sudo netstat -tlnp | grep -E '1883|18083'
else
    echo "❌ EMQX 启动失败，查看日志:"
    sudo journalctl -u emqx -n 10 --no-pager
fi

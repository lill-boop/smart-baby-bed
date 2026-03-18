#!/bin/bash
# 最终修复脚本

echo "=== 停止服务 ==="
sudo systemctl stop emqx
sudo pkill -9 emqx

echo "=== 重写配置 ==="
sudo cat > /etc/emqx/emqx.conf <<EOF
node {
  name = "emqx@127.0.0.1"
  cookie = "emqxsecret"
  data_dir = "/var/lib/emqx"
}

cluster {
  discovery_strategy = manual
}

dashboard {
  listeners.http {
    bind = "0.0.0.0:18083"
  }
}

listeners.tcp.default {
  bind = "0.0.0.0:1883"
  max_connections = 100000
}

mqtt {
  allow_anonymous = true
}

log {
  console {
     level = warning
  }
}
EOF

echo "=== 清理旧数据 ==="
# 清除旧的节点数据，否则新节点名无法生效
sudo rm -rf /var/lib/emqx/*

echo "=== 启动服务 ==="
sudo systemctl start emqx
echo "等待 10s..."
sleep 10

echo "=== 检查结果 ==="
if sudo netstat -tlnp | grep 1883; then
    echo "SUCCESS: 端口 1883 已监听！"
else
    echo "FAIL: 端口未监听，查看日志："
    sudo journalctl -u emqx -n 20 --no-pager
fi

import socket
import threading
import select

# 配置
LOCAL_HOST = '0.0.0.0'
LOCAL_PORT = 8888
REMOTE_HOST = '192.168.0.222'
REMOTE_PORT = 1883

def handle_client(client_socket):
    try:
        # 连接远程服务器
        remote_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        remote_socket.connect((REMOTE_HOST, REMOTE_PORT))
        print(f"✅ 已连接到远程 EMQX: {REMOTE_HOST}:{REMOTE_PORT}")
    except Exception as e:
        print(f"❌ 无法连接远程 EMQX: {e}")
        client_socket.close()
        return

    # 数据转发循环
    try:
        while True:
            r, w, x = select.select([client_socket, remote_socket], [], [])
            if client_socket in r:
                data = client_socket.recv(4096)
                if len(data) == 0: break
                remote_socket.send(data)
                print(f"-> 发送 {len(data)} 字节到服务器")
            
            if remote_socket in r:
                data = remote_socket.recv(4096)
                if len(data) == 0: break
                client_socket.send(data)
                print(f"<- 收到 {len(data)} 字节回传给客户端")
    except Exception as e:
        print(f"连接断开: {e}")
    finally:
        client_socket.close()
        remote_socket.close()
        print("连接关闭")

def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.bind((LOCAL_HOST, LOCAL_PORT))
    server.listen(5)
    print(f"🚀 MQTT 转发代理正在监听: {LOCAL_PORT}")
    print(f"🎯 转发目标: {REMOTE_HOST}:{REMOTE_PORT}")
    print("等待设备连接...")

    while True:
        client_sock, addr = server.accept()
        print(f"📡 收到设备连接! 来自: {addr[0]}:{addr[1]}")
        client_handler = threading.Thread(target=handle_client, args=(client_sock,))
        client_handler.start()

if __name__ == '__main__':
    main()

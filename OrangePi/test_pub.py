
import socket
import time

def send_mqtt_publish(topic, payload):
    # 构建 MQTT CONNECT 报文
    # Fixed Header: 0x10 (CONNECT), Remaining Length
    # Variable Header: Protocol Name (MQTT), Protocol Level (4), Connect Flags (2), Keep Alive (60)
    # Payload: Client ID (test_pub)
    connect_packet = bytearray([
        0x10, 0x14, # Fixed Header
        0x00, 0x04, ord('M'), ord('Q'), ord('T'), ord('T'), # Protocol Name
        0x04, # Level
        0x02, # Flags (Clean Session)
        0x00, 0x3C, # Keep Alive
        0x00, 0x08, ord('t'), ord('e'), ord('s'), ord('t'), ord('_'), ord('p'), ord('u'), ord('b') # Client ID
    ])

    # 构建 MQTT PUBLISH 报文
    # Fixed Header: 0x30 (PUBLISH), Remaining Length
    # Variable Header: Topic Length, Topic
    # Payload: Data
    topic_len = len(topic)
    payload_len = len(payload)
    remaining_len = 2 + topic_len + payload_len
    
    publish_packet = bytearray()
    publish_packet.append(0x30)
    publish_packet.append(remaining_len)
    publish_packet.append(topic_len >> 8)
    publish_packet.append(topic_len & 0xFF)
    publish_packet.extend(topic.encode('utf-8'))
    publish_packet.extend(payload.encode('utf-8'))
    
    # 建立 TCP 连接
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(5)
    try:
        sock.connect(('192.168.0.222', 1883))
        print("Connected to Broker")
        
        sock.send(connect_packet)
        # 简单等待 CONNACK
        time.sleep(0.1)
        
        sock.send(publish_packet)
        print(f"Sent: {payload} to {topic}")
        
        sock.close()
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    send_mqtt_publish("device/esp8266_01/status", '{"temp_x10":385,"fan_flag":1}')

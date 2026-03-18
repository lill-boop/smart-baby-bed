#include <arpa/inet.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define APP_PORT_RX 12348
#define ESP_PORT_RX 12347

#define ESP_TARGET_IP "192.168.0.161"
#define ESP_TARGET_PORT 12346

// Global state for App Address
struct sockaddr_in app_addr;
int has_app_addr = 0;
pthread_mutex_t addr_lock = PTHREAD_MUTEX_INITIALIZER;

void *handle_app_to_esp(void *arg) {
  int sock;
  struct sockaddr_in server_addr, client_addr, esp_addr;
  char buffer[2048];
  socklen_t addr_len = sizeof(client_addr);

  if ((sock = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
    perror("Socket creation failed");
    exit(1);
  }

  int opt = 1;
  setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

  // Increase receive buffer for better performance
  int rcvbuf = 65536; // 64KB receive buffer
  setsockopt(sock, SOL_SOCKET, SO_RCVBUF, &rcvbuf, sizeof(rcvbuf));

  memset(&server_addr, 0, sizeof(server_addr));
  server_addr.sin_family = AF_INET;
  server_addr.sin_addr.s_addr = INADDR_ANY;
  server_addr.sin_port = htons(APP_PORT_RX);

  if (bind(sock, (const struct sockaddr *)&server_addr, sizeof(server_addr)) <
      0) {
    perror("Bind failed APP_PORT_RX");
    exit(1);
  }

  memset(&esp_addr, 0, sizeof(esp_addr));
  esp_addr.sin_family = AF_INET;
  esp_addr.sin_port = htons(ESP_TARGET_PORT);
  inet_pton(AF_INET, ESP_TARGET_IP, &esp_addr.sin_addr);

  printf("Listening on %d (App -> ESP)\n", APP_PORT_RX);

  while (1) {
    int n = recvfrom(sock, buffer, sizeof(buffer), 0,
                     (struct sockaddr *)&client_addr, &addr_len);
    if (n > 0) {
      // Update App Address (from ANY packet)
      pthread_mutex_lock(&addr_lock);
      if (!has_app_addr ||
          app_addr.sin_addr.s_addr != client_addr.sin_addr.s_addr ||
          app_addr.sin_port != client_addr.sin_port) {
        app_addr = client_addr;
        has_app_addr = 1;
        char ip[INET_ADDRSTRLEN];
        inet_ntop(AF_INET, &client_addr.sin_addr, ip, INET_ADDRSTRLEN);
        printf("APP CONNECTED: %s:%d\n", ip, ntohs(client_addr.sin_port));
      }
      pthread_mutex_unlock(&addr_lock);

      // Forward to ESP32 (NO FILTER - ESP32 handles filtering)
      sendto(sock, buffer, n, 0, (const struct sockaddr *)&esp_addr,
             sizeof(esp_addr));
    }
  }
  return NULL;
}

void *handle_esp_to_app(void *arg) {
  int sock;
  struct sockaddr_in server_addr, client_addr;
  char buffer[2048];
  socklen_t addr_len = sizeof(client_addr);

  if ((sock = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
    perror("Socket creation failed");
    exit(1);
  }

  int opt = 1;
  setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

  // Increase receive buffer for better performance
  int rcvbuf = 65536; // 64KB receive buffer
  setsockopt(sock, SOL_SOCKET, SO_RCVBUF, &rcvbuf, sizeof(rcvbuf));

  memset(&server_addr, 0, sizeof(server_addr));
  server_addr.sin_family = AF_INET;
  server_addr.sin_addr.s_addr = INADDR_ANY;
  server_addr.sin_port = htons(ESP_PORT_RX);

  if (bind(sock, (const struct sockaddr *)&server_addr, sizeof(server_addr)) <
      0) {
    perror("Bind failed ESP_PORT_RX");
    exit(1);
  }

  printf("Listening on %d (ESP -> App)\n", ESP_PORT_RX);

  while (1) {
    int n = recvfrom(sock, buffer, sizeof(buffer), 0,
                     (struct sockaddr *)&client_addr, &addr_len);
    if (n > 0) {
      // Forward to App if known
      pthread_mutex_lock(&addr_lock);
      if (has_app_addr) {
        sendto(sock, buffer, n, 0, (const struct sockaddr *)&app_addr,
               sizeof(app_addr));
      }
      pthread_mutex_unlock(&addr_lock);
    }
  }
  return NULL;
}

int main() {
  printf("=== HIGH PERFORMANCE C RELAY ===\n");
  pthread_t t1, t2;
  pthread_create(&t1, NULL, handle_app_to_esp, NULL);
  pthread_create(&t2, NULL, handle_esp_to_app, NULL);
  pthread_join(t1, NULL);
  pthread_join(t2, NULL);
  return 0;
}

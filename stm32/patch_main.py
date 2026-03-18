import os

file_path = r"c:\Users\54257\Desktop\Project_C8111、\Project_C8\Project_C8\Core\Src\main.c"

new_content = b"""void HAL_UART_RxCpltCallback(UART_HandleTypeDef *huart)
{
  if(huart->Instance == Huart_wifi.Instance)
  {
      // Push to RingBuffer
      if(!RB_Push(&esp8266_rb, uartwifi_value)) {
          uint8_t d;
          RB_Pop(&esp8266_rb, &d);
          RB_Push(&esp8266_rb, uartwifi_value);
          rb_overflow_flag = 1;
      }
      HAL_UART_Receive_IT(&Huart_wifi, &uartwifi_value, 1);
  }
}"""

try:
    with open(file_path, 'rb') as f:
        data = f.read()
    
    # Locate start of function
    start_marker = b"void HAL_UART_RxCpltCallback(UART_HandleTypeDef *huart)"
    start_idx = data.find(start_marker)
    
    if start_idx == -1:
        print("Error: Function start not found")
        exit(1)
        
    # Locate end of function (assuming it's the last function or matching braces)
    # The previous `type` output showed it as the last function before Error_Handler?
    # No, it was before `/* USER CODE END 4 */`.
    
    # Let's find the closing brace.
    # We can scan forward from start_idx.
    # Count braces.
    
    current_idx = start_idx
    brace_count = 0
    found_first_brace = False
    end_idx = -1
    
    for i in range(start_idx, len(data)):
        byte = data[i:i+1]
        if byte == b'{':
            brace_count += 1
            found_first_brace = True
        elif byte == b'}':
            brace_count -= 1
            if found_first_brace and brace_count == 0:
                end_idx = i + 1
                break
    
    if end_idx == -1:
        print("Error: Function body end not found")
        exit(1)
        
    # Performs replacement
    new_data = data[:start_idx] + new_content + data[end_idx:]
    
    with open(file_path, 'wb') as f:
        f.write(new_data)
        
    print("SUCCESS: main.c patched.")

except Exception as e:
    print(f"Error: {e}")

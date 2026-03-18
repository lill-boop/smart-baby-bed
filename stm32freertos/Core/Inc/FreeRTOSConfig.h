#ifndef FREERTOS_CONFIG_H
#define FREERTOS_CONFIG_H

#if defined(__ICCARM__) || defined(__CC_ARM) || defined(__GNUC__)
    #include <stdint.h>
    extern uint32_t SystemCoreClock;
#endif

// 基础配置
#define configUSE_PREEMPTION                    1           // 开启抢占式调度
#define configUSE_TICKLESS_IDLE                 0
#define configCPU_CLOCK_HZ                      (SystemCoreClock) // 自动获取主频(通常是72MHz)
#define configTICK_RATE_HZ                      ((TickType_t)1000) // 1ms 的时间片
#define configMAX_PRIORITIES                    (5)         // 最大优先级 0-4
#define configMINIMAL_STACK_SIZE                ((uint16_t)128) // 最小任务栈
#define configMAX_TASK_NAME_LEN                 (16)
#define configUSE_16_BIT_TICKS                  0
#define configIDLE_SHOULD_YIELD                 1

// 内存分配相关
#define configSUPPORT_DYNAMIC_ALLOCATION        1           // 支持动态内存分配
#define configTOTAL_HEAP_SIZE                   ((size_t)(10 * 1024)) // 堆大小给个 10KB 够用

// 软件定时器
#define configUSE_TIMERS                        0
#define configTIMER_TASK_PRIORITY               (configMAX_PRIORITIES - 1)
#define configTIMER_QUEUE_LENGTH                10
#define configTIMER_TASK_STACK_DEPTH            configMINIMAL_STACK_SIZE

// 钩子函数（Hook）暂时不需要
#define configUSE_IDLE_HOOK                     0
#define configUSE_TICK_HOOK                     0
#define configCHECK_FOR_STACK_OVERFLOW          0

// 宏 API 开启
#define INCLUDE_vTaskPrioritySet                1
#define INCLUDE_uxTaskPriorityGet               1
#define INCLUDE_vTaskDelete                     1
#define INCLUDE_vTaskCleanUpResources           1
#define INCLUDE_vTaskSuspend                    1
#define INCLUDE_vTaskDelayUntil                 1
#define INCLUDE_vTaskDelay                      1
#define INCLUDE_xTaskGetSchedulerState          1

/* ======= 中断优先级配置 =======
   STM32F103 使用 4 位优先级 (0-15), 数字越小优先级越高.
   configKERNEL_INTERRUPT_PRIORITY = 最低优先级 (15 左移4位 = 0xF0)
   configMAX_SYSCALL_INTERRUPT_PRIORITY = FreeRTOS 能管理的最高中断优先级
   优先级高于此值(数字更小)的中断不会被 FreeRTOS 临界区屏蔽, 
   可用于超高实时性需求(如电机脉冲), 但不能调用 FreeRTOS API */
#define configPRIO_BITS                         4
#define configLIBRARY_LOWEST_INTERRUPT_PRIORITY 15
#define configLIBRARY_MAX_SYSCALL_INTERRUPT_PRIORITY 5
#define configKERNEL_INTERRUPT_PRIORITY         (configLIBRARY_LOWEST_INTERRUPT_PRIORITY << (8 - configPRIO_BITS))
#define configMAX_SYSCALL_INTERRUPT_PRIORITY    (configLIBRARY_MAX_SYSCALL_INTERRUPT_PRIORITY << (8 - configPRIO_BITS))

/* ======= 最最核心的：中断映射！ ======= 
   将 FreeRTOS 的内核中断挂接到 STM32 的中断向量表上 */
#define vPortSVCHandler         SVC_Handler
#define xPortPendSVHandler      PendSV_Handler
// SysTick_Handler 会在 HAL 库的 IT.c 中手动实现

#endif /* FREERTOS_CONFIG_H */

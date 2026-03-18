/**********************************
作者：特纳斯电子
网站：https://www.mcude.com
联系方式：46580829(QQ)
淘宝店铺：特纳斯电子
**********************************/

#ifndef __DS18B20_H__
#define __DS18B20_H__


/**********************************
包含头文件
**********************************/
#include "main.h"


/**********************************
PIN口定义
**********************************/
#define DQ_GPIO_CLK_ENABLE()     			__HAL_RCC_GPIOA_CLK_ENABLE()
#define DQ_Pin GPIO_PIN_8
#define DQ_GPIO_Port GPIOA

#define DQ_DATA(a)	(a?HAL_GPIO_WritePin(DQ_GPIO_Port,DQ_Pin,GPIO_PIN_SET):HAL_GPIO_WritePin(DQ_GPIO_Port,DQ_Pin,GPIO_PIN_RESET))
#define DQ_Read   HAL_GPIO_ReadPin(DQ_GPIO_Port,DQ_Pin)

/**********************************
函数声明
**********************************/
void Ds18b20_GPIO_Init(void);							//DS18B20初始化函数
uint16_t Ds18b20_Read_Temp(void);					//读取温度值函数



//使用调用


#endif


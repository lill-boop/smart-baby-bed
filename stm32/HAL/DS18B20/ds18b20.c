/**********************************
作者：特纳斯电子
网站：https://www.mcude.com
联系方式：46580829(QQ)
淘宝店铺：特纳斯电子
**********************************/

/**********************************
包含头文件
**********************************/
#include "./HAL/ds18b20/ds18b20.h"
#include "./HAL/delay/delay.h"

/****
*******DS18B20引脚初始化函数
*****/
void Ds18b20_GPIO_Init(void)
{
	GPIO_InitTypeDef GPIO_InitStruct = {0};
	
	DQ_GPIO_CLK_ENABLE();
	
  /*Configure GPIO pin : DQ_Pin */
  GPIO_InitStruct.Pin = DQ_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_PULLUP;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_HIGH;
  HAL_GPIO_Init(DQ_GPIO_Port, &GPIO_InitStruct);
}
/**********************************
作者：特纳斯电子
网站：https://www.mcude.com
联系方式：46580829(QQ)
淘宝店铺：特纳斯电子
*******DS18B20引脚输出配置
*****/
void DQ_OUT(void)
{
	GPIO_InitTypeDef GPIO_InitStruct = {0};
	
	GPIO_InitStruct.Pin = DQ_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_PULLUP;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_HIGH;
  HAL_GPIO_Init(DQ_GPIO_Port, &GPIO_InitStruct);
}

/****
*******DS18B20引脚输入配置
*****/
void DQ_IN(void)
{
	GPIO_InitTypeDef GPIO_InitStruct = {0};
	
	GPIO_InitStruct.Pin = DQ_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_INPUT;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  HAL_GPIO_Init(DQ_GPIO_Port, &GPIO_InitStruct);
}

/****
*******DS18B20初始化函数
*****/
void Ds18b20_Init(void)
{
	DQ_OUT();													//引脚配置为输出模式
	DQ_DATA(1);												//把总线拿高
	delay_us(15);	    								//15us
	DQ_DATA(0);												//给复位脉冲
	delay_us(750);										//750us
	DQ_DATA(1);												//把总线拿高 等待
	delay_us(110);										//110us
	DQ_IN();													//引脚配置为输入模式
	delay_us(200);										//200us
	DQ_OUT();													//引脚配置为输出模式
	DQ_DATA(1);												//把总线拿高 释放总线
}

/****
*******写ds18b20内的数据函数
*****/
void write_18b20(uint8_t dat)
{
	uint8_t i;
	DQ_OUT();													//引脚配置为输出模式
	for(i=0;i<8;i++)
	{												 						//写数据是低位开始
		DQ_DATA(0);												//把总线拉低写时间隙开始 
		delay_us(1);
		if(dat&0x01)											 //向18b20总线写数据了
			DQ_DATA(1);
		else
			DQ_DATA(0);
		delay_us(60);	 										// 60us
		DQ_DATA(1);											  //释放总线
		dat>>=1;
	}	
}

/****
*******读取ds18b20内的数据函数
*****/
uint8_t read_18b20(void)
{
 uint8_t i,value;
 for(i=0;i<8;i++)
 {
	DQ_OUT();													//引脚配置为输出模式
  DQ_DATA(0);												//把总线拿低读时间隙开始 
	delay_us(1);
  value>>= 1;							 					//读数据是低位开始
  DQ_DATA(1);											  //释放总线
	DQ_IN();													//引脚配置为输入模式
  if(DQ_Read==1)								  	//开始读写数据 
  value|=0x80;
  delay_us(60);	 										//60us	读一个时间隙最少要保持60us的时间
 }
 return value;						 					//返回数据
}

/****
*******读取温度值函数
*******返回值：温度值(扩大10倍)
*****/
uint16_t Ds18b20_Read_Temp(void)
{
 uint16_t value;
 uint8_t low;								  				//在读取温度的时候如果中断的太频繁了，就应该把中断给关了，否则会影响到18b20的时序
 Ds18b20_Init();		  							//初始化18b20
 write_18b20(0xcc);	  							//跳过64位ROM
 write_18b20(0x44);	  							//启动一次温度转换命令
 delay_us(500);											//500us

 Ds18b20_Init();		 			  				//初始化18b20	
 write_18b20(0xcc);	  							//跳过64位ROM
 write_18b20(0xbe);	 			  				//发出读取暂存器命令

 low=read_18b20();				  				//读温度低字节
 value=read_18b20();			  				//读温度高字节

 value<<=8;								  				//把温度的高位左移8位
 value|=low;							  				//把读出的温度低位放到value的低八位中
 value*=0.625;				      				//转换到温度值 小数
 return value;						  				//返回读出的温度 带小数
}
/**********************************
作者：特纳斯电子
网站：https://www.mcude.com
联系方式：46580829(QQ)
淘宝店铺：特纳斯电子
**********************************/

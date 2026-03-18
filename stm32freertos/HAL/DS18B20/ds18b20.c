/**********************************
浣滆咃細鐗圭撼鏂鐢靛瓙
缃戠珯锛歨ttps://www.mcude.com
鑱旂郴鏂瑰紡锛46580829(QQ)
娣樺疂搴楅摵锛氱壒绾虫柉鐢靛瓙
**********************************/

/**********************************
鍖呭惈澶存枃浠
**********************************/
#include "../../HAL/ds18b20/ds18b20.h"
#include "../../HAL/delay/delay.h"

/****
*******DS18B20寮曡剼鍒濆嬪寲鍑芥暟
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
浣滆咃細鐗圭撼鏂鐢靛瓙
缃戠珯锛歨ttps://www.mcude.com
鑱旂郴鏂瑰紡锛46580829(QQ)
娣樺疂搴楅摵锛氱壒绾虫柉鐢靛瓙
*******DS18B20寮曡剼杈撳嚭閰嶇疆
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
*******DS18B20寮曡剼杈撳叆閰嶇疆
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
*******DS18B20鍒濆嬪寲鍑芥暟
*****/
void Ds18b20_Init(void)
{
	DQ_OUT();													//寮曡剼閰嶇疆涓鸿緭鍑烘ā寮
	DQ_DATA(1);												//鎶婃荤嚎鎷块珮
	delay_us(15);	    								//15us
	DQ_DATA(0);												//缁欏嶄綅鑴夊啿
	delay_us(750);										//750us
	DQ_DATA(1);												//鎶婃荤嚎鎷块珮 绛夊緟
	delay_us(110);										//110us
	DQ_IN();													//寮曡剼閰嶇疆涓鸿緭鍏ユā寮
	delay_us(200);										//200us
	DQ_OUT();													//寮曡剼閰嶇疆涓鸿緭鍑烘ā寮
	DQ_DATA(1);												//鎶婃荤嚎鎷块珮 閲婃斁鎬荤嚎
}

/****
*******鍐檇s18b20鍐呯殑鏁版嵁鍑芥暟
*****/
void write_18b20(uint8_t dat)
{
	uint8_t i;
	DQ_OUT();													//寮曡剼閰嶇疆涓鸿緭鍑烘ā寮
	for(i=0;i<8;i++)
	{												 						//鍐欐暟鎹鏄浣庝綅寮濮
		DQ_DATA(0);												//鎶婃荤嚎鎷変綆鍐欐椂闂撮殭寮濮 
		delay_us(1);
		if(dat&0x01)											 //鍚18b20鎬荤嚎鍐欐暟鎹浜
			DQ_DATA(1);
		else
			DQ_DATA(0);
		delay_us(60);	 										// 60us
		DQ_DATA(1);											  //閲婃斁鎬荤嚎
		dat>>=1;
	}	
}

/****
*******璇诲彇ds18b20鍐呯殑鏁版嵁鍑芥暟
*****/
uint8_t read_18b20(void)
{
 uint8_t i,value;
 for(i=0;i<8;i++)
 {
	DQ_OUT();													//寮曡剼閰嶇疆涓鸿緭鍑烘ā寮
  DQ_DATA(0);												//鎶婃荤嚎鎷夸綆璇绘椂闂撮殭寮濮 
	delay_us(1);
  value>>= 1;							 					//璇绘暟鎹鏄浣庝綅寮濮
  DQ_DATA(1);											  //閲婃斁鎬荤嚎
	DQ_IN();													//寮曡剼閰嶇疆涓鸿緭鍏ユā寮
  if(DQ_Read==1)								  	//寮濮嬭诲啓鏁版嵁 
  value|=0x80;
  delay_us(60);	 										//60us	璇讳竴涓鏃堕棿闅欐渶灏戣佷繚鎸60us鐨勬椂闂
 }
 return value;						 					//杩斿洖鏁版嵁
}

/****
*******璇诲彇娓╁害鍊煎嚱鏁
*******杩斿洖鍊硷細娓╁害鍊(鎵╁ぇ10鍊)
*****/
uint16_t Ds18b20_Read_Temp(void)
{
 uint16_t value;
 uint8_t low;								  				//鍦ㄨ诲彇娓╁害鐨勬椂鍊欏傛灉涓鏂鐨勫お棰戠箒浜嗭紝灏卞簲璇ユ妸涓鏂缁欏叧浜嗭紝鍚﹀垯浼氬奖鍝嶅埌18b20鐨勬椂搴
 Ds18b20_Init();		  							//鍒濆嬪寲18b20
 write_18b20(0xcc);	  							//璺宠繃64浣峈OM
 write_18b20(0x44);	  							//鍚鍔ㄤ竴娆℃俯搴﹁浆鎹㈠懡浠
 delay_us(500);											//500us

 Ds18b20_Init();		 			  				//鍒濆嬪寲18b20	
 write_18b20(0xcc);	  							//璺宠繃64浣峈OM
 write_18b20(0xbe);	 			  				//鍙戝嚭璇诲彇鏆傚瓨鍣ㄥ懡浠

 low=read_18b20();				  				//璇绘俯搴︿綆瀛楄妭
 value=read_18b20();			  				//璇绘俯搴﹂珮瀛楄妭

 value<<=8;								  				//鎶婃俯搴︾殑楂樹綅宸︾Щ8浣
 value|=low;							  				//鎶婅诲嚭鐨勬俯搴︿綆浣嶆斁鍒皏alue鐨勪綆鍏浣嶄腑
 value*=0.625;				      				//杞鎹㈠埌娓╁害鍊 灏忔暟
 return value;						  				//杩斿洖璇诲嚭鐨勬俯搴 甯﹀皬鏁
}
/**********************************
浣滆咃細鐗圭撼鏂鐢靛瓙
缃戠珯锛歨ttps://www.mcude.com
鑱旂郴鏂瑰紡锛46580829(QQ)
娣樺疂搴楅摵锛氱壒绾虫柉鐢靛瓙
**********************************/

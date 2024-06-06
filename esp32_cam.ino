#include <Arduino.h>
#include <WiFi.h>
#include "esp_camera.h"
#include <vector>
 //esp32_cam
#define maxcache 1024  //图像数据包的大小
 
const char* ssid = "****";
const char* password = "*******";
 
const int LED = 4;//闪光灯
const int ZHESHI_LED = 33; //指示灯 
bool cam_state = true;  //是否开启摄像头传输
const int port = 8080;
String  frame_begin = "FrameBegin"; //图像传输包头
String  frame_over = "FrameOverr";  //图像传输包尾
String  msg_begin = "Esp32Msg";  //消息传输头
//创建服务器端
WiFiServer server;
//创建客户端
WiFiClient client;
 
//CAMERA_MODEL_AI_THINKER类型摄像头的引脚定义
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
 
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22
 
static camera_config_t camera_config = {
    .pin_pwdn = PWDN_GPIO_NUM,
    .pin_reset = RESET_GPIO_NUM,
    .pin_xclk = XCLK_GPIO_NUM,
    .pin_sscb_sda = SIOD_GPIO_NUM,
    .pin_sscb_scl = SIOC_GPIO_NUM,
    
    .pin_d7 = Y9_GPIO_NUM,
    .pin_d6 = Y8_GPIO_NUM,
    .pin_d5 = Y7_GPIO_NUM,
    .pin_d4 = Y6_GPIO_NUM,
    .pin_d3 = Y5_GPIO_NUM,
    .pin_d2 = Y4_GPIO_NUM,
    .pin_d1 = Y3_GPIO_NUM,
    .pin_d0 = Y2_GPIO_NUM,
    .pin_vsync = VSYNC_GPIO_NUM,
    .pin_href = HREF_GPIO_NUM,
    .pin_pclk = PCLK_GPIO_NUM,
    
    .xclk_freq_hz = 20000000,
    .ledc_timer = LEDC_TIMER_0,
    .ledc_channel = LEDC_CHANNEL_0,
    
    .pixel_format = PIXFORMAT_JPEG,
    .frame_size = FRAMESIZE_VGA,
    .jpeg_quality = 31,   //图像质量   0-63  数字越小质量越高
    .fb_count = 1,
};
//初始化摄像头
esp_err_t camera_init() {
    //initialize the camera
    esp_err_t err = esp_camera_init(&camera_config);
    if (err != ESP_OK) {
        Serial.println("Camera Init Failed!");
        return err;
    }
    sensor_t * s = esp_camera_sensor_get();
    //initial sensors are flipped vertically and colors are a bit saturated
    if (s->id.PID == OV2640_PID) {
    //        s->set_vflip(s, 1);//flip it back
    //        s->set_brightness(s, 1);//up the blightness just a bit
    //        s->set_contrast(s, 1);
    }
    Serial.println("Camera Init OK!");
    return ESP_OK;
}
 
bool wifi_init(const char* ssid,const char* password ){
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false); //关闭STA模式下wifi休眠，提高响应速度
  #ifdef staticIP
    WiFi.config(staticIP, gateway, subnet);
  #endif
  WiFi.begin(ssid, password);
  uint8_t i = 0;
  Serial.println();
  while (WiFi.status() != WL_CONNECTED && i++ < 20) {
      delay(500);
      Serial.print(".");
  }
  if (i == 21) {
    Serial.println();
    Serial.print("Could not connect to"); 
    Serial.println(ssid);
    digitalWrite(ZHESHI_LED,HIGH);  //网络连接失败 熄灭指示灯
    return false;
  }
  Serial.print("Connecting to wifi "); 
  Serial.print(ssid);
  Serial.println(" success!"); 
  digitalWrite(ZHESHI_LED,LOW);  //网络连接成功 点亮指示灯
  return true;
}
 
void TCPServerInit(){
  //启动server
  server.begin(port);
  //关闭小包合并包功能，不会延时发送数据
  server.setNoDelay(true);
  Serial.print("Ready! TCP Server");
  Serial.print(WiFi.localIP());
  Serial.println(":8080 Running!");
}
void cssp(){
  camera_fb_t * fb = esp_camera_fb_get();
  uint8_t * temp = fb->buf; //这个是为了保存一个地址，在摄像头数据发送完毕后需要返回，否则会出现板子发送一段时间后自动重启，不断重复
  if (!fb)
  {
      Serial.println("Camera Capture Failed");
  }
  else
  { 
    //先发送Frame Begin 表示开始发送图片 然后将图片数据分包发送 每次发送1430 余数最后发送 
    //完毕后发送结束标志 Frame Over 表示一张图片发送完毕 
    client.print(frame_begin); //一张图片的起始标志
    // 将图片数据分段发送
    int leng = fb->len;
    int timess = leng/maxcache;
    int extra = leng%maxcache;
    for(int j = 0;j< timess;j++)
    {
      client.write(fb->buf, maxcache); 
      for(int i =0;i< maxcache;i++)
      {
        fb->buf++;
      }
    }
    client.write(fb->buf, extra);
    client.print(frame_over);      // 一张图片的结束标志
    //Serial.print("This Frame Length:");
    //Serial.print(fb->len);
    //Serial.println(".Succes To Send Image For TCP!");
    //return the frame buffer back to the driver for reuse
    fb->buf = temp; //将当时保存的指针重新返还
    esp_camera_fb_return(fb);  //这一步在发送完毕后要执行，具体作用还未可知。        
  }
  //delay(20);//短暂延时 增加数据传输可靠性        
}
void TCPServerMonitor(){
if (server.hasClient()) {
  if ( client && client.connected()) {
    WiFiClient serverClient = server.available();
    serverClient.stop();
    Serial.println("Connection rejected!");
  }else{
    //分配最新的client
    client = server.available();
    client.println(msg_begin +  "Client is Connect!");
    Serial.println("Client is Connect!");
  }
}
  //检测client发过来的数据
if (client && client.connected()) {
  if (client.available()) {
    String line = client.readStringUntil('\n'); //读取数据到换行符
    if (line == "CamOFF"){
      cam_state = false;
      client.println(msg_begin +  "Camera OFF!");
    }
    if (line == "CamON"){
      cam_state = true;
      client.println(msg_begin +  "Camera ON!");
    }
    if (line == "LedOFF"){
      digitalWrite(LED, LOW);
      client.println(msg_begin +  "Led OFF!");
    }
    if (line == "LedON"){
      digitalWrite(LED, HIGH);
      client.println(msg_begin +  "Led ON!");
    }
    Serial.println(line);
  }
}
  
// 视频传输
if(cam_state)
{
  if (client && client.connected()) {
    cssp();
  }
}
}
 
void setup() {
  Serial.begin(115200);
  pinMode(ZHESHI_LED, OUTPUT);
  digitalWrite(ZHESHI_LED, HIGH);
  pinMode(LED, OUTPUT);
  digitalWrite(LED, LOW);
  wifi_init(ssid,password);
  camera_init();
  TCPServerInit();
}
 
void loop() {
  TCPServerMonitor();
}
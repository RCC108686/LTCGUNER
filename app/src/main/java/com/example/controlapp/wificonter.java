package com.example.controlapp;


import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

public class wificonter extends AppCompatActivity{
    EditText host_editText,port_editText,send_data;
    TextView rec_data;
    Button connect_button,send;
    ImageView show_cam;
    Socket socket;
    InputStream inputStream;
    OutputStream outputStream;
    byte[] RevBuff = new byte[1024];  //定义接收数据流的包的大小
    MyHandler myHandler;
    byte[] temp = new byte[0];  //存放一帧图像的数据
    int headFlag = 0;    // 0 数据流不是图像数据   1 数据流是图像数据
    Bitmap bitmap = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
               super.onCreate(savedInstanceState);

                setContentView(R.layout.activity_wificonter);

        host_editText = findViewById(R.id.host_editText); //服务器地址
        port_editText = findViewById(R.id.port_editText);//服务器端口
        connect_button= findViewById(R.id.connect_button);//连接服务器按钮
        rec_data = findViewById(R.id.rec_data); //存放接收到的非图像数据
        send = findViewById(R.id.send);//发送数据按钮
        send_data = findViewById(R.id.send_data);//发送数据文本框
        connect_button.setText("连接"); //设置连接按钮名称为连接 如果已连接上显示断开
        show_cam = findViewById(R.id.show_cam); //存放图像数据
        myHandler = new MyHandler();
//        连接服务器操作
        connect_button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if(connect_button.getText() == "连接"){
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Message msg = myHandler.obtainMessage();
                            try {
                                //如果 host_editText  port_editText为空的话 点击连接 会退出程序
                                socket = new Socket((host_editText.getText()).toString(),Integer.valueOf(port_editText.getText().toString()));
                                //socket = new Socket("192.168.0.3",8080);
                                if(socket.isConnected()){
                                    msg.what = 0;//显示连接服务器成功信息
                                    inputStream = socket.getInputStream();
                                    outputStream = socket.getOutputStream();
                                    Recv();//接收数据
                                }else{
                                    msg.what = 1;//显示连接服务器失败信息
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                msg.what = 1;//显示连接服务器失败信息
                            }
                            myHandler.sendMessage(msg);
                        }
                    }).start();
                }else{
//                    关闭socket连接
                    try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
                    try { inputStream.close(); }catch (IOException e) { e.printStackTrace(); }
                    try { outputStream.close(); }catch (IOException e) { e.printStackTrace(); }
                    connect_button.setText("连接");
                }
            }
        });
//        发送数据
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
//                            发送数据
                            outputStream.write(send_data.getText().toString().getBytes());
                        } catch (IOException e) {
//                            如果发送数据失败 显示连接服务器失败信息
                            e.printStackTrace();
                            Message msg = myHandler.obtainMessage();
                            msg.what = 1;
                            myHandler.sendMessage(msg);
                        }
                    }
                }).start();
            }
        });
    }
    //    接收数据方法
    public void Recv(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(socket != null && socket.isConnected()){
                    try {
                        int Len = inputStream.read(RevBuff);
                        if(Len != -1){
//                          图像数据包的头  FrameBegin
                            boolean begin_cam_flag = RevBuff[0] == 70 && RevBuff[1] == 114 && RevBuff[2] == 97 && RevBuff[3] == 109 && RevBuff[4] == 101
                                    && RevBuff[5] == 66 && RevBuff[6] == 101 && RevBuff[7] == 103 && RevBuff[8] == 105 && RevBuff[9] == 110 ;
//                            图像数据包的尾  FrameOverr
                            boolean end_cam_flag = RevBuff[0] == 70 && RevBuff[1] == 114 && RevBuff[2] == 97 && RevBuff[3] == 109 && RevBuff[4] == 101
                                    && RevBuff[5] == 79 && RevBuff[6] == 118 && RevBuff[7] == 101 && RevBuff[8] == 114 && RevBuff[9] == 114;
//                            判断接收的包是不是图片的开头数据 是的话s说明下面的数据属于图片数据 将headFlag置1
                            if(headFlag == 0 && begin_cam_flag){
                                headFlag = 1;
                            }else if(end_cam_flag){  //判断包是不是图像的结束包 是的话 将数据传给 myHandler  3 同时将headFlag置0
                                Message msg = myHandler.obtainMessage();
                                msg.what = 3;
                                myHandler.sendMessage(msg);
                                headFlag = 0;
                            }else if(headFlag == 1){ //如果 headFlag == 1 说明包是图像数据  将数据发给byteMerger方法 合并一帧图像
                                temp = byteMerger(temp,RevBuff);
                            }
//                            定义包头 Esp32Msg  判断包头 在向myHandler  2 发送数据    eadFlag == 0 && !end_cam_flag没用 会展示图像的数据
                            boolean begin_msg_begin = RevBuff[0] == 69 && RevBuff[1] == 115 && RevBuff[2] == 112 && RevBuff[3] == 51 && RevBuff[4] == 50
                                    && RevBuff[5] == 77 && RevBuff[6] == 115 && RevBuff[7] == 103 ;
                            if(begin_msg_begin){
                                Message msg = myHandler.obtainMessage();
                                msg.what = 2;
                                msg.arg1 = Len;
                                msg.obj = RevBuff;
                                myHandler.sendMessage(msg);
                            }
                        }else{
//                            如果Len = -1 说明接受异常  显示连接服务器失败信息  跳出循环
                            Message msg = myHandler.obtainMessage();
                            msg.what = 1;
                            myHandler.sendMessage(msg);
                            break;
                        }
                    } catch (IOException e) {
//                        如果接受数据inputStream.read(RevBuff)语句执行失败 显示连接服务器失败信息  跳出循环
                        e.printStackTrace();
                        Message msg = myHandler.obtainMessage();
                        msg.what = 1;
                        myHandler.sendMessage(msg);
                        break;
                    }
                }
            }
        }).start();
    }

    //    合并一帧图像数据  a 全局变量 temp   b  接受的一个数据包 RevBuff
    public byte[] byteMerger(byte[] a,byte[] b){
        int i = a.length + b.length;
        byte[] t = new byte[i]; //定义一个长度为 全局变量temp  和 数据包RevBuff 一起大小的字节数组 t
        System.arraycopy(a,0,t,0,a.length);  //先将 temp（先传过来的数据包）放进  t
        System.arraycopy(b,0,t,a.length,b.length);//然后将后进来的这各数据包放进t
        return t; //返回t给全局变量 temp
    }
    //处理一些不能在线程里面执行的信息
    class MyHandler extends Handler{
        public void handleMessage(Message msg){
            super.handleMessage(msg);
            switch (msg.what){
                case 0:
//                    连接服务器成功信息
                    Toast.makeText(wificonter.this,"连接服务器成功！",Toast.LENGTH_SHORT).show();
                    connect_button.setText("断开");
                    break;
                case 1:
//                    连接服务器失败信息
                    Toast.makeText(wificonter.this,"连接服务器失败！",Toast.LENGTH_SHORT).show();
                    break;
                case 2:
//                    处理接收到的非图像数据
                    byte[] Buffer = new byte[msg.arg1];
                    System.arraycopy((byte[])msg.obj,0,Buffer,0,msg.arg1);
                    SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
                    Date date = new Date(System.currentTimeMillis());
                    String content = (new String(Buffer)) + "----"  + formatter.format(date) + "\n";
                    rec_data.append(content);
                    break;
                case 3:
//                    处理接受到的图像数据 并展示
                    bitmap = BitmapFactory.decodeByteArray(temp, 0,temp.length);
                    show_cam.setImageBitmap(bitmap);//这句就能显示图片(bitmap数据没问题的情况下) 存在图像闪烁情况 待解决
                    temp = new byte[0];  //一帧图像显示结束  将 temp清零
                    break;
                default: break;
            }
        }
    }
    //    销毁窗体 释放资源
    protected void onDestroy() {
        super.onDestroy();
        if(inputStream != null){
            try {inputStream.close();}catch(IOException e) {e.printStackTrace();}
        }

        if(outputStream != null){
            try {outputStream.close();} catch (IOException e) {e.printStackTrace();}
        }
        if(socket != null){
            try {socket.close();} catch (IOException e) {e.printStackTrace();}
        }
    }
}//.wificonter



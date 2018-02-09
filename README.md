# BlueToothCom
蓝牙串口形式的通讯框架。支持的工作方式仅限于串行发送接收的形式，包含经典蓝牙和低功耗蓝牙（BLE）两种模式。

一、blue包中为蓝牙通讯框架，demo包中为使用demo。

二、基本思路和api简介

1. 将蓝牙操作有关的读写、开关操作封装到单例BlueManager中。使用BluetoothAdapter来开关设备蓝牙，运行时权限需要自行处理。
另外BlueManager还承担了监控蓝牙连接状态、蓝牙通道状态等任务，并可以选择广播、通知栏、吐司等形式通知用户或者组件。

2. 将蓝牙实际读写及其通道的建立（如经典蓝牙的配对、低功耗蓝牙的获取服务等）封装到服务BlueService中。

3. 将一些配置以常量的形式，存储在BlueConfig类中。使用时需要根据实际需要调整具体参数。

4. 由于蓝牙通讯与通讯协议关系紧密，为了方便解耦，设计了一个标识不同协议头文件的接口ProtocolSign，使用时需要根据实际使用的协议，
实现此接口，并在读取响应时作为参数传入。

5. 暂不支持断线重连。

6. 支持心跳包筛查，但不支持错误报文的容错排查，一旦接收到不符合协议标识的报文，则直接断开连接。

7. 没有过多封装UI等，但做了相当多的解耦，方便复用，如核心类BlueManager和BlueService甚至可以互相解耦。

三、使用简介

1. 在应用中初始化单例
> `BlueManager.init(this);`

2. 在需要使用的地方获取BlueManager单例
> `mBlueManager = BlueManager.getInstance();`

3. 设置模式（默认经典蓝牙）和目标设备的蓝牙MAC。暂时只支持按MAC来自动搜索设备并连接，如需要显示搜索列表或者按照设备名称，请自行扩展，我毕竟还是懒~
> `mBlueManager.setMode(BlueConfig.MODE_LE);`

> `mBlueManager.reset("A4:D5:78:0E:4A:0B");`

4. 判断蓝牙通道的状态，开始读写数据。注意，**读取时要传入协议标识接口的实现**。
> ```
>   if(mBlueManager.write(bytes)){
>     byte[] result = mBlueManager.read(ProtocolSign13761.getInstance());
>     if(null != result){
>       // 解析报文result
>     }
>   }
  也可以使用另外一种方式：
> ```
>   StringBuffer buffer = new StringBuffer();
>   if(mBlueManager.conversation(downFrame, buffer, ProtocolSign13761.getInstance())){
>     //解析报文buffer
>   }
  在进行蓝牙读写之前，最好先进行状态的判断：
> `mBlueManager.isConnect();`

5. 关闭蓝牙
> `mBlueManager.close();`

6. 如果需要实时关心蓝牙状态，可以在需要监控连接状态的页面（也可以是任何地方）注册本地广播，即可收到相关事件。
> ```
>/**
> * 蓝牙连接状态监控本地广播
>*/
>   private final BroadcastReceiver BLUE_STATE_RECEIVER = new BroadcastReceiver() {
>     @Override
>     public void onReceive(Context context, Intent intent) {
>         if (BlueManager.BLUE_STATE_ACTION.equalsIgnoreCase(intent.getAction())) {
>           // onStateChanged(intent.getIntExtra(BlueConfig.EXTRA_KEY_STATUS, -1), intent.getStringExtra(BlueConfig.EXTRA_KEY_DES));
>         }
>     }
>   };

>```
>    @Override
>    protected void onCreate(@Nullable Bundle savedInstanceState) {
>        super.onCreate(savedInstanceState);
>        LocalBroadcastManager.getInstance(this).registerReceiver(BLUE_STATE_RECEIVER, BlueManager.BLUE_STATE_INTENT_FILTER);
>    }

>```
>   @Override
>    protected void onDestroy() {
>        LocalBroadcastManager.getInstance(this).unregisterReceiver(BLUE_STATE_RECEIVER);
>        super.onDestroy();
>    }
    

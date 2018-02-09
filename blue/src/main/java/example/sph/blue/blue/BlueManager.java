package example.sph.blue.blue;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicInteger;

import example.sph.blue.R;

/**
 * 蓝牙助手类，单例，代理所有蓝牙操作。主要功能：蓝牙的开关和读写、蓝牙搜索、蓝牙状态广播监测、蓝牙通道连接状态监测。
 *
 * @author ShiPengHao
 * @date 2018/2/4
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class BlueManager {
    private final String TAG = "BlueManager";
    /**
     * 蓝牙连接状态监控Action
     */
    public static final String BLUE_STATE_ACTION = "BlueToothConnectionState";
    /**
     * 蓝牙连接状态监控IntentFilter
     */
    public static final IntentFilter BLUE_STATE_INTENT_FILTER = new IntentFilter(BLUE_STATE_ACTION);
    /*
     * ctx
     */
    private final Context mContext;
    /**
     * 蓝牙adapter
     */
    private final BluetoothAdapter BlueAdapter = BluetoothAdapter.getDefaultAdapter();
    /**
     * 目标蓝牙设备mac地址
     */
    private String mRemoteMac;
    /**
     * 扫描设备定时时间
     */
    private long mScanEndMillis;
    /**
     * 蓝牙广播接收器
     */
    private final BroadcastReceiver BlueReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equalsIgnoreCase(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                BlueLog.i(TAG, "ACTION_FOUND:" + device.getName() + "," + device.getAddress());
                if (mRemoteMac.equalsIgnoreCase(device.getAddress())) {
                    connectDevice(device);
                    BlueAdapter.cancelDiscovery();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equalsIgnoreCase(action)) {
                BlueLog.i(TAG, "ACTION_DISCOVERY_FINISHED");
                if (BlueAdapter.isEnabled() && null == mBlueConn) {
                    if (System.currentTimeMillis() > mScanEndMillis) {
                        // 继续扫描
                        scan();
                    } else {
                        ConnListenerProxy.onDiscoveryFailed(BlueAdapter.getRemoteDevice(mRemoteMac));
                        BlueAdapter.cancelDiscovery();
                    }
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equalsIgnoreCase(action)) {
                BlueLog.i(TAG, "ACTION_STATE_CHANGED");
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -100);
                if (BluetoothAdapter.STATE_ON == state) {
                    BlueLog.i(TAG, "STATE_ON");
                    if (null == mBlueConn) {
                        scan();
                    }
                } else if (BluetoothAdapter.STATE_OFF == state) {
                    BlueLog.i(TAG, "STATE_OFF");
                    if (null != mBlueConn) {
                        ConnListenerProxy.onDisconnect(BlueAdapter.getRemoteDevice(mRemoteMac));
                    }
                }
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equalsIgnoreCase(action)) {
                BlueLog.i(TAG, "ACTION_BOND_STATE_CHANGED");
                if (null != mBlueBinder && null != mRemoteMac) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -100);
                    BlueLog.i(TAG, "bind state :" + state);
                    if (null != device && mRemoteMac.equalsIgnoreCase(device.getAddress()) && state == BluetoothDevice.BOND_BONDED) {
                        mBlueBinder.setBonded();
                    }
                }
            }
        }

    };

    /**
     * 蓝牙服务连接对象
     */
    private ServiceConnection mBlueConn;
    /**
     * 蓝牙服务binder
     */
    private BlueService.BlueSocketBinder mBlueBinder;
    /**
     * 处理蓝牙连接状态回调的Handler，应该在主线程中初始化
     */
    private final Handler BlueCallbackHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            String msg = (String) message.obj;
            BlueLog.i(TAG, "handler message:" + msg);
            notifyConnState(message.what, msg);
            ConnStatus.set(message.what);
            if (message.what != STATUS_CONNECT) {
                close();
            }
            return true;
        }
    });

    /**
     * 蓝牙服务监听代理
     */
    private final BlueConnListener ConnListenerProxy = new BlueConnListener() {

        /**
         * 拿到蓝牙连接状态变化之后，设置状态并弹出通知，并发送广播
         * @param msg 状态变化的消息
         * @param status 状态
         */
        private void onResult(String msg, int status) {
            Message message = Message.obtain();
            message.what = status;
            message.obj = msg;
            BlueCallbackHandler.sendMessage(message);
        }

        @Override
        public void onConnect(BluetoothDevice remoteDevice) {
            onResult(mContext.getString(R.string.blue_conn_status_connect) + "MAC:" + remoteDevice.getAddress(), STATUS_CONNECT);
        }

        @Override
        public void onDisconnect(BluetoothDevice remoteDevice) {
            onResult(mContext.getString(R.string.blue_conn_status_disconnect) + "MAC:" + remoteDevice.getAddress(), STATUS_DISCONNECT);
        }

        @Override
        public void onPairedFailed(BluetoothDevice remoteDevice) {
            onResult(mContext.getString(R.string.blue_conn_status_paired_error) + "MAC:" + remoteDevice.getAddress(), STATUS_PAIRED_ERROR);
        }

        @Override
        public void onDiscoveryFailed(BluetoothDevice remoteDevice) {
            onResult(mContext.getString(R.string.blue_conn_status_no_device) + "MAC:" + remoteDevice.getAddress(), STATUS_PAIRED_ERROR);
        }
    };

    /**
     * 连接状态：正常
     */
    public static final int STATUS_CONNECT = 0;
    /**
     * 连接状态：断开连接
     */
    public static final int STATUS_DISCONNECT = 1;
    /**
     * 连接状态：配对失败
     */
    public static final int STATUS_PAIRED_ERROR = 2;
    /**
     * 连接状态：还未建立连接
     */
    public static final int STATUS_UN_REQUEST = 3;
    /**
     * 连接状态：正在搜索蓝牙设备
     */
    public static final int STATUS_SEARCHING = 4;
    /**
     * 连接状态：经典蓝牙正在配对
     */
    public static final int STATUS_PAIRING = 6;
    /**
     * 连接状态：低功耗蓝牙正在获取服务
     */
    public static final int STATUS_LE_GET_SERVICE = 7;
    /**
     * 状态
     */
    private final AtomicInteger ConnStatus = new AtomicInteger(STATUS_UN_REQUEST);

    /**
     * 单例
     */
    @SuppressLint("StaticFieldLeak")
    private static BlueManager instance;
    /**
     * 模式：默认经典蓝牙
     */
    @BlueConfig.Mode
    private int mMode = BlueConfig.MODE_CLASSIC;
    /**
     * 低功耗蓝牙搜索回调
     */
    private final BluetoothAdapter.LeScanCallback LeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            BlueLog.i(TAG, "LeScanCallback::onLeScan, device MAC:" + device.getAddress());
            if (null != mRemoteMac && mRemoteMac.equalsIgnoreCase(device.getAddress())) {
                connectDevice(device);
                BlueAdapter.stopLeScan(this);
            } else if (System.currentTimeMillis() > mScanEndMillis) {
                ConnListenerProxy.onDiscoveryFailed(BlueAdapter.getRemoteDevice(mRemoteMac));
                BlueAdapter.stopLeScan(this);
            }
        }
    };


    /**
     * 初始化，构造单例，最好放在应用初始化时。基于延迟加载考虑的话也可以后延，但需注意空指针异常。
     *
     * @param context ctx
     */
    @MainThread
    public synchronized static void init(@NonNull Context context) {
        if (null == instance) {
            instance = new BlueManager(context);
        }
    }

    /**
     * 私有构造
     *
     * @param context ctx
     */
    private BlueManager(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * 获取实例
     *
     * @return 单例
     */
    public static BlueManager getInstance() {
        return instance;
    }

    /**
     * 复位蓝牙通道
     *
     * @param mac 目标蓝牙mac
     */
    public synchronized void reset(@NonNull String mac) {
        // 当前设备已经建立连接，无需再次操作
        if (isConnect() && mac.equalsIgnoreCase(mRemoteMac)) {
            return;
        }
        mContext.startActivity(new Intent(mContext, BlueConnActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        close(false, false);
        mRemoteMac = mac.toUpperCase();
        open();
    }

    /**
     * 打开蓝牙通道
     */
    private void open() {
        if (null == mRemoteMac || !mRemoteMac.matches(BlueConfig.MAC_PATTERN)) {
            throw new IllegalStateException(mContext.getString(R.string.blue_illegal_bt_mac));
        }
        BlueLog.i(TAG, "open address:" + mRemoteMac);
        registerBTReceiver();
        // 如果蓝牙已经启动，则搜索目标设备，否则开启蓝牙，在状态回调中开始搜索设备
        if (BlueAdapter.isEnabled()) {
            scan();
        } else {
            BlueAdapter.enable();
        }
    }

    /**
     * 关闭当前连接通道和蓝牙，不显示通知，通常用于应用内的状态回调或者应用退出
     */
    public void close() {
        close(true, false);
    }

    /**
     * 关闭当前连接通道
     *
     * @param closeBlue 是否关闭蓝牙，一般情况下传入true，用于切换设备时传入false
     * @param showTip   是否显示通知，一般情况下传入false，用于在应用中手动关闭蓝牙通道时传入true
     */
    public void close(boolean closeBlue, boolean showTip) {
        BlueLog.i(TAG, "close");
        BlueCallbackHandler.removeCallbacksAndMessages(null);
        if (null == mRemoteMac) {
            BlueLog.i(TAG, "manager had been closed , return");
            ConnStatus.set(STATUS_UN_REQUEST);
            return;
        }
        if (showTip) {
            BlueLog.i(TAG, "show tip");
            notifyConnState(STATUS_DISCONNECT, mContext.getString(R.string.blue_conn_status_disconnect) + "MAC:" + mRemoteMac);
        }
        BlueLog.i(TAG, "close address:" + mRemoteMac);
        BlueLog.i(TAG, "unregisterReceiver");
        mContext.unregisterReceiver(BlueReceiver);
        if (null != mBlueConn) {
            BlueLog.i(TAG, "unbindService");
            mContext.unbindService(mBlueConn);
        }
        if (BlueAdapter.isDiscovering()) {
            BlueLog.i(TAG, "cancelDiscovery");
            BlueAdapter.cancelDiscovery();
        }
        if (mMode == BlueConfig.MODE_LE) {
            BlueLog.i(TAG, "stopLeScan");
            BlueAdapter.stopLeScan(LeScanCallback);
        }
        if (closeBlue) {
            BlueLog.i(TAG, "disable blue");
            BlueAdapter.disable();
        }
        mRemoteMac = null;
        mBlueBinder = null;
        mBlueConn = null;
        mScanEndMillis = 0;
        ConnStatus.set(STATUS_UN_REQUEST);
        BlueLog.i(TAG, "reset member v");
    }

    /**
     * 查询蓝牙通道状态
     *
     * @return 状态
     */
    public int getStatus() {
        return ConnStatus.get();
    }

    /**
     * 蓝牙是否连接成功
     *
     * @return true是
     */
    public boolean isConnect() {
        return ConnStatus.get() == STATUS_CONNECT;
    }

    /**
     * 完成一次会话：向蓝牙通道写一帧报文，并读取一帧报文
     *
     * @param hexString    要发送的报文16进制字符串
     * @param buffer       接收响应报文16进制字符串的StringBuffer
     * @param protocolSign 协议标识
     * @return 操作结果，成功true,否则false
     */
    @WorkerThread
    public synchronized boolean conversation(@NonNull String hexString, StringBuffer buffer, ProtocolSign protocolSign) {
        if (write(DataFormatter.hexString2Bytes(hexString))) {// 连接正常
            BlueLog.i(TAG, "conversation write ok:" + hexString);
            byte[] result = read(protocolSign);
            if (null == result) {
                BlueLog.i(TAG, "conversation read failed");
            } else {
                buffer.delete(0, buffer.length());
                buffer.append(DataFormatter.bytes2HexString(result));
                BlueLog.i(TAG, "conversation read ok:" + buffer.toString());
                return true;
            }
        } else {
            BlueLog.i(TAG, "conversation write failed");
        }
        return false;
    }

    /**
     * 蓝牙通道写字节码
     *
     * @param bytes 报文字节码
     * @return 操作结果，成功true,否则false
     */
    @WorkerThread
    public synchronized boolean write(byte[] bytes) {
        if (null != mBlueBinder) {// 服务状态正常
            int count = 1;
            do {
                if (isConnect()) {// 蓝牙通道正常
                    if (mBlueBinder.write(bytes)) {
                        BlueLog.i(TAG, "write:" + DataFormatter.bytes2HexString(bytes));
                        return true;
                    } else {
                        BlueLog.i(TAG, "write error, attempt time:" + count);
                        count++;
                    }
                } else {
                    BlueLog.i(TAG, "write error, bluetooth disconnect");
                    break;
                }
            } while (count < 4);
        } else {
            BlueLog.i(TAG, "write error, service disconnect");
        }
        return false;
    }

    /**
     * 马上读取一帧字节码
     *
     * @param protocolSign 协议标识
     * @return 报文字节码。如果解析数据有误，返回null
     */
    @WorkerThread
    public synchronized byte[] read(ProtocolSign protocolSign) {
        if (null != mBlueBinder && isConnect()) {
            byte[] head = mBlueBinder.readBytes(protocolSign.getSignLen());
            if (null == head) {
                if (isConnect()) {
                    ConnListenerProxy.onDisconnect(BlueAdapter.getRemoteDevice(mRemoteMac));
                }
                BlueLog.i(TAG, "read empty head, return");
                return null;
            }
            if (protocolSign.checked(head)) {
                int len = protocolSign.getBodyLen(head);
                byte[] body = mBlueBinder.readBytes(len);
                if (null == body) {
                    if (isConnect()) {
                        ConnListenerProxy.onDisconnect(BlueAdapter.getRemoteDevice(mRemoteMac));
                    }
                    BlueLog.i(TAG, "read empty body, return");
                    return null;
                }
                len = head.length + body.length;
                byte[] result = new byte[len];
                System.arraycopy(head, 0, result, 0, head.length);
                System.arraycopy(body, 0, result, head.length, body.length);
                BlueLog.i(TAG, "read:" + DataFormatter.bytes2HexString(result));
                if (protocolSign.filterHeartFrame(result)) {
                    BlueLog.i(TAG, "this frame is a heart frame, drop it, read next frame");
                    return read(protocolSign);
                } else {
                    return result;
                }
            } else if (isConnect()) {
                BlueLog.i(TAG, "head unchecked, disconnect channel. head:" + DataFormatter.bytes2HexString(head));
                ConnListenerProxy.onDisconnect(BlueAdapter.getRemoteDevice(mRemoteMac));
            } else {
                BlueLog.i(TAG, "head unchecked, return. head:" + DataFormatter.bytes2HexString(head));
            }
        } else {
            BlueLog.i(TAG, "unConnect , return directly");
        }
        return null;
    }

    /**
     * 注册蓝牙相关事件广播
     */
    private void registerBTReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.setPriority(Integer.MAX_VALUE);
        mContext.registerReceiver(BlueReceiver, filter);
    }

    /**
     * 通知连接状态
     *
     * @param status 状态
     * @param msg    状态信息
     */
    private void notifyConnState(int status, String msg) {
        if (BlueConfig.SHOW_STATE_LOCAL_BROADCAST) {
            Intent intent = new Intent(BLUE_STATE_ACTION);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent.putExtra(BlueConfig.EXTRA_KEY_DES, msg).putExtra(BlueConfig.EXTRA_KEY_STATUS, status));
        }
        if (BlueConfig.SHOW_STATE_NOTIFICATION) {
            sendNotification(msg);
        }
        if (BlueConfig.SHOW_STATE_TOAST) {
            Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 向通知栏发送蓝牙状态通知
     *
     * @param msg 通知内容
     */
    private void sendNotification(String msg) {
        final NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            @SuppressWarnings("deprecation")
            Notification notification = new NotificationCompat.Builder(mContext)
                    .setSmallIcon(R.drawable.bluetooth)
                    .setTicker(mContext.getString(R.string.blue_status))
                    .setContentTitle(msg.substring(0, msg.indexOf("MAC")))
                    .setContentText(msg.substring(msg.indexOf("MAC")))
                    .setWhen(System.currentTimeMillis())
                    .setDefaults(NotificationCompat.DEFAULT_SOUND)
                    .setAutoCancel(true)
                    .build();
            notification.flags = Notification.FLAG_AUTO_CANCEL;
            manager.notify(BlueConfig.BLUE_NOTIFICATION_ID, notification);
        }
    }

    /**
     * 清除蓝牙状态的通知
     */
    public void clearNotification() {
        if (BlueConfig.SHOW_STATE_NOTIFICATION) {
            NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (null != manager) {
                manager.cancel(BlueConfig.BLUE_NOTIFICATION_ID);
            }
        }
    }

    /**
     * 设置蓝牙模式
     *
     * @param mode 模式
     */
    public void setMode(@BlueConfig.Mode int mode) {
        this.mMode = mode;
    }

    /**
     * 开始扫描附近蓝牙设备，发现目标设备后停止扫描，开始连接
     */
    private void scan() {
        BlueLog.i(TAG, "start scan, mode:" + mMode);
        if (mScanEndMillis < System.currentTimeMillis()) {
            BlueLog.i(TAG, "set scan limit time");
            mScanEndMillis = System.currentTimeMillis() + BlueConfig.SCAN_WAIT_TIME_SECONDS * 1000;
        }
        if (mMode == BlueConfig.MODE_CLASSIC) {
            try {
                BlueAdapter.cancelDiscovery();
            } catch (Exception e) {
                e.printStackTrace();
            }
            BlueAdapter.startDiscovery();
        } else if (mMode == BlueConfig.MODE_LE) {
            try {
                BlueAdapter.stopLeScan(LeScanCallback);
            } catch (Exception e) {
                e.printStackTrace();
            }
            BlueAdapter.startLeScan(LeScanCallback);
        }
        Intent intent = new Intent(BLUE_STATE_ACTION);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent.putExtra(BlueConfig.EXTRA_KEY_DES, mContext.getString(R.string.blue_conn_status_searching)).putExtra(BlueConfig.EXTRA_KEY_STATUS, STATUS_SEARCHING));
    }

    /**
     * 开启与该设备的数据连接，这里交给服务去执行
     *
     * @param device 目标蓝牙设备
     */
    private void connectDevice(final BluetoothDevice device) {
        // 运行期间仅执行一次，连接失败的回调也交给页面处理
        if (null == mBlueConn) {
            Intent intent = new Intent(BLUE_STATE_ACTION);
            if (mMode == BlueConfig.MODE_CLASSIC) {
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent.putExtra(BlueConfig.EXTRA_KEY_DES, mContext.getString(R.string.blue_conn_status_pairing)).putExtra(BlueConfig.EXTRA_KEY_STATUS, STATUS_PAIRING));
            } else {
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent.putExtra(BlueConfig.EXTRA_KEY_DES, mContext.getString(R.string.blue_conn_status_le_get_service)).putExtra(BlueConfig.EXTRA_KEY_STATUS, STATUS_LE_GET_SERVICE));
            }
            mBlueConn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    if (null == service) {
                        throw new IllegalArgumentException(mContext.getString(R.string.blue_illegal_service_intent_params));
                    } else {
                        BlueLog.i(TAG, "get service binder");
                        mBlueBinder = (BlueService.BlueSocketBinder) service;
                        mBlueBinder.setListener(ConnListenerProxy);
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    BlueLog.i(TAG, "onServiceDisconnected");
                    ConnListenerProxy.onDisconnect(device);
                }
            };
            Intent service = new Intent(mContext, BlueService.class)
                    .putExtra(BlueConfig.EXTRA_KEY_MAC, mRemoteMac)
                    .putExtra(BlueConfig.EXTRA_KEY_MODE, mMode);
            mContext.bindService(service, mBlueConn, Context.BIND_AUTO_CREATE);
        }
    }

}

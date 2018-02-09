package example.sph.blue.blue;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * <p>蓝牙通讯服务，主要完成配对、通道建立、数据读写。</p>
 *
 * @author ShiPengHao
 * @date 2017/2/4
 */
public class BlueService extends Service {
    private final String TAG = "BlueService";
    /**
     * 通讯服务binder
     */
    private BlueSocketBinder mBinder;

    /**
     * 通讯服务功能binder
     */
    class BlueSocketBinder extends Binder {
        /**
         * 远程蓝牙设备
         */
        private final BluetoothDevice mRemoteDevice;
        /**
         * 模式：经典、低功耗
         */
        @BlueConfig.Mode
        private final int mMode;
        /**
         * 缓存读取到的字节流的队列，不管是哪种模式，都将通道读取到的数据缓存入此队列末尾，都从此队列头部读取
         */
        private final ConcurrentLinkedQueue<Integer> mReadBytes = new ConcurrentLinkedQueue<>();
        /**
         * 运行标记
         */
        private volatile boolean mRunningFlag = true;
        /**
         * 经典蓝牙socket输入流
         */
        private InputStream mInputStream;
        /**
         * 经典蓝牙socket输出流
         */
        private OutputStream mOutputStream;
        /**
         * 蓝牙通道状态监听器
         */
        private BlueConnListener mListener;
        /**
         * 经典蓝牙设备绑定成功标记，保持线程间的可见性
         */
        private volatile boolean mBonded;
        /**
         * 低功耗蓝牙连接
         */
        private BluetoothGatt mLeGATT;
        /**
         * 低功耗蓝牙Characteristic
         */
        private BluetoothGattCharacteristic mGATTCharacter;
        /**
         * 低功耗蓝牙mtu，默认20
         */
        private int mGATTMtu = 20;
//        /**
//         * 低功耗蓝牙模式下，正在发送数据的标记。发送时设true，发送回调设false
//         */
//        private volatile boolean isSending;
        /**
         * 低功耗蓝牙处理接收数据的handler
         */
        private Handler mLeReceiveHandler;

        /**
         * 创建并开启工作线程，连接设备，读写数据
         *
         * @param mac  目标远程设备mac地址
         * @param mode 模式：经典、低功耗
         */
        private BlueSocketBinder(@NonNull String mac, @BlueConfig.Mode int mode) {
            BlueLog.i(TAG, "create BlueSocketBinder");
            mMode = mode;
            // 根据mac地址获取设备
            mRemoteDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    SystemClock.sleep(500);// 等待客户端（BlueManager）设置listener
                    BlueLog.i(TAG, "mode:" + mMode);
                    if (mMode == BlueConfig.MODE_LE) {
                        startLE();
                    } else if (mMode == BlueConfig.MODE_CLASSIC) {
                        startClassic();
                    }

                }
            }).start();
            if (mMode == BlueConfig.MODE_LE) {
                HandlerThread handlerThread = new HandlerThread("LeReceiveHandler");
                handlerThread.start();
                Looper looper = handlerThread.getLooper();
                mLeReceiveHandler = new Handler(looper) {
                    @Override
                    public void handleMessage(Message msg) {
                        receiveByteFromCharacter((byte[]) msg.obj);
                    }
                };
            }
        }

        /**
         * 设置客户端监听
         *
         * @param listener 客户端监听
         */
        void setListener(BlueConnListener listener) {
            mListener = listener;
        }

        /**
         * 停止工作，释放资源
         */
        private void release() {
            mRunningFlag = false;
            if (mMode == BlueConfig.MODE_LE) {
                mLeReceiveHandler.removeCallbacksAndMessages(null);
                mLeReceiveHandler.getLooper().quitSafely();
                mLeGATT.disconnect();
                mLeGATT.close();
                return;
            }
            if (null != mInputStream) {
                try {
                    mInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != mOutputStream) {
                try {
                    mOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * 发生异常时，按照断开连接事件来处理
         */
        private void onException() {
            BlueLog.i(TAG, "service exception");
            onConnStatusChanged(false);
        }

        /**
         * 处理连接状态。如果有监听器，调用其回调；如果没有，自己处理
         *
         * @param isConnect 连接true，断开连接false
         */
        private void onConnStatusChanged(boolean isConnect) {
            if (isConnect) {
                if (null != mListener) {
                    // 使用新线程，防止直接在蓝牙连接的线程中进行后续操作
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            mListener.onConnect(mRemoteDevice);
                        }
                    }).start();
                }
            } else {
                if (null == mListener) {
                    stopSelf();
                } else {
                    mListener.onDisconnect(mRemoteDevice);
                }
            }
        }

        /**
         * 经典蓝牙：从输入流中不停的读取数据，每次1个字节
         */
        private void receiveByteFromStream() throws IOException {
            int readByte;
            while (mRunningFlag) {
                readByte = mInputStream.read();
                if (-1 != readByte) {
                    BlueLog.i(TAG, "cache byte classic:" + DataFormatter.byte2HexString((byte) readByte));
                    mReadBytes.offer(readByte);
                    SystemClock.sleep(5);
                }
            }
        }

        /**
         * 低功耗蓝牙，在蓝牙连接回调中主动调用此方法，读取蓝牙数据区缓存的字节
         *
         * @param value 数据
         */
        private void receiveByteFromCharacter(byte[] value) {
            BlueLog.i(TAG, "cache bytes le: " + DataFormatter.bytes2HexString(value));
            for (byte readByte : value) {
                mReadBytes.offer((int) readByte);
                SystemClock.sleep(5);
            }
        }

        /**
         * 读取指定数量的字节
         *
         * @param count 数量
         * @return 字节数组
         */
        @Nullable
        synchronized byte[] readBytes(@IntRange(from = 1, to = 512) int count) {
            if (mRunningFlag) {
                BlueLog.i(TAG, "read bytes start. count:" + count);
                long deadTime = System.currentTimeMillis() + BlueConfig.READ_WAIT_TIME_MILLIS;
                byte[] result = new byte[count];
                int index = 0;
                byte readByte;
                while (index < count && System.currentTimeMillis() < deadTime) {
                    if (!mRunningFlag) {
                        BlueLog.i(TAG, "service is not running, return");
                        return null;
                    }
                    Integer integer = mReadBytes.poll();
                    if (null == integer) {
                        SystemClock.sleep(5);
                    } else {
                        readByte = (byte) (integer.intValue());
                        result[index++] = readByte;
                        BlueLog.i(TAG, "read byte hex:" + DataFormatter.byte2HexString(readByte));
                    }
                }
                BlueLog.i(TAG, "read bytes over:" + DataFormatter.bytes2HexString(result));
                return result;
            } else {
                BlueLog.i(TAG, "service is not running, return");
                return null;
            }
        }

        /**
         * 写数据
         *
         * @param bytesHexString 字节码16进制字符串
         * @return 正确发送并准备接收true，否则false
         */
        synchronized boolean write(String bytesHexString) {
            return write(DataFormatter.hexString2Bytes(bytesHexString));
        }

        /**
         * 写数据
         *
         * @param bytes 字节码
         * @return 正确发送并准备接收true，否则false
         */
        synchronized boolean write(byte[] bytes) {
            if (mRunningFlag) {
                BlueLog.i(TAG, "write start: " + DataFormatter.bytes2HexString(bytes));
                try {
                    if (mMode == BlueConfig.MODE_CLASSIC) {
                        mOutputStream.write(bytes);
                    } else if (mMode == BlueConfig.MODE_LE) {
                        final int LENGTH = bytes.length;
                        final int MTU = mGATTMtu - mGATTMtu % 10;
                        BlueLog.i(TAG, String.format("mtu:%s, len:%s", MTU, LENGTH));
                        if (LENGTH <= MTU) {
                            BlueLog.i(TAG, "write once");
                            return mGATTCharacter.setValue(bytes)
                                    && mLeGATT.writeCharacteristic(mGATTCharacter);
//                                    && mLeGATT.readCharacteristic(mGATTCharacter);
                        } else {
                            byte[] buffer;
                            int count = 0;
                            while (count < LENGTH) {
                                if (!mRunningFlag) {
                                    BlueLog.i(TAG, "service is not running, return");
                                    return false;
                                }
                                int bufferLen = (LENGTH - count) > MTU ? MTU : (LENGTH - count);
                                if (bufferLen == 0) {
                                    break;
                                }
                                buffer = new byte[bufferLen];
                                System.arraycopy(bytes, count, buffer, 0, bufferLen);
                                if (mGATTCharacter.setValue(buffer) && mLeGATT.writeCharacteristic(mGATTCharacter)) {
                                    count += bufferLen;
                                    BlueLog.i(TAG, "write package:" + DataFormatter.bytes2HexString(buffer));
                                    SystemClock.sleep(20);
                                } else {
                                    BlueLog.i(TAG, "write package failed");
                                    return false;
                                }
                            }
                            return true;
//                            return mLeGATT.readCharacteristic(mGATTCharacter);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    onException();
                }
            } else {
                BlueLog.i(TAG, "service is not running");
            }
            return false;
        }

        /**
         * 开始低功耗蓝牙连接和通道建立
         */
        private void startLE() {
            BlueLog.i(TAG, "conn: le");
            mLeGATT = mRemoteDevice.connectGatt(BlueService.this, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (mRunningFlag) {
                        BlueLog.i(TAG, "BluetoothGattCallback::onConnectionStateChange");
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                                BlueLog.i(TAG, "BluetoothGattCallback::onConnectionStateChange:connect");
                                if (gatt.discoverServices()) {
                                    BlueLog.i(TAG, "discoverServices");
                                }
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                onConnStatusChanged(false);
                            }
                        }
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (mRunningFlag && null == mGATTCharacter) {
                        BlueLog.i(TAG, "BluetoothGattCallback::onServicesDiscovered");
                        BluetoothGattService gattService = gatt.getService(BlueConfig.UUID_LE_SERVICE);
                        if (null == gattService) {
                            BlueLog.i(TAG, "no such service");
                            onConnStatusChanged(false);
                            return;
                        }
                        mGATTCharacter = gattService.getCharacteristic(BlueConfig.UUID_LE_CHARACTERISTIC);
                        if (null == mGATTCharacter) {
                            BlueLog.i(TAG, "no such Characteristic");
                            onConnStatusChanged(false);
                            return;
                        }
                        if (gatt.setCharacteristicNotification(mGATTCharacter, true)) {
                            BlueLog.i(TAG, "BluetoothGattCallback::onServicesDiscovered:getCharacteristic:setCharacteristicNotification:Ok");
                            BluetoothGattDescriptor descriptor = mGATTCharacter.getDescriptor(BlueConfig.UUID_LE_DESCRIPTOR_CLIENT);
                            if (null == descriptor) {
                                BlueLog.i(TAG, "no such descriptor");
                                onConnStatusChanged(false);
                                return;
                            }
                            BlueLog.i(TAG, "BluetoothGattCallback::onServicesDiscovered:descriptor uuid:" + descriptor.getUuid().toString());
                            if (descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                                BlueLog.i(TAG, "write local descriptor value ok");
                                if (gatt.writeDescriptor(descriptor)) {
                                    BlueLog.i(TAG, "write remote descriptor value ok");
                                    BlueLog.i(TAG, "finally connect success");
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        gatt.requestMtu(512);
                                    }
                                    onConnStatusChanged(true);
                                } else {
                                    BlueLog.i(TAG, "write remote descriptor value failed");
                                    onConnStatusChanged(false);
                                }
                            } else {
                                BlueLog.i(TAG, "write local descriptor value failed");
                                onConnStatusChanged(false);
                            }
                        } else {
                            BlueLog.i(TAG, "setCharacteristicNotification failed");
                            onConnStatusChanged(false);
                        }
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    if (mRunningFlag) {
                        final byte[] value = characteristic.getValue();
                        if (null != value) {
                            BlueLog.i(TAG, "BluetoothGattCallback::onCharacteristicChanged:" + DataFormatter.bytes2HexString(value));
                            Message message = Message.obtain();
                            message.obj = value;
                            mLeReceiveHandler.sendMessage(message);
                        }
                    }
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    if (mRunningFlag) {
//                        new Thread(new Runnable() {
//                            @Override
//                            public void run() {
//                                isSending = false;
//                                BlueLog.i(TAG, "BluetoothGattCallback::onCharacteristicWrite, set SendingFlag false.");
//                            }
//                        }).start();
                        byte[] value = characteristic.getValue();
                        BlueLog.i(TAG, "BluetoothGattCallback::onCharacteristicWrite:" + DataFormatter.bytes2HexString(value));
                    }
                }

                @Override
                public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                    if (mRunningFlag) {
                        BlueLog.i(TAG, "BluetoothGattCallback::onMtuChanged:" + mtu);
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            synchronized (BlueSocketBinder.class) {
                                mGATTMtu = mtu;
                            }
                        }
                    }
                }
            });
        }

        /**
         * 开始经典蓝牙连接和通道建立
         */
        private void startClassic() {
            BlueLog.i(TAG, "conn: classic");
            try {
                connClassic();
                receiveByteFromStream();
            } catch (IOException e) {
                e.printStackTrace();
                onException();
            }
        }

        /**
         * 连接经典蓝牙
         *
         * @throws IOException 异常
         */
        private void connClassic() throws IOException {
            BlueLog.i(TAG, "conn: classic");
            bindDevice();
            if (mBonded) {
                BlueLog.i(TAG, "bind success");
                // 与目标简历的连接对象
                BluetoothSocket bluetoothSocket = mRemoteDevice.createRfcommSocketToServiceRecord(BlueConfig.UUID_CLASSIC_COM);
                // 阻塞
                bluetoothSocket.connect();
                mInputStream = bluetoothSocket.getInputStream();
                mOutputStream = bluetoothSocket.getOutputStream();
                BlueLog.i(TAG, "connected");
                onConnStatusChanged(true);
                mRunningFlag = true;
            } else {
                BlueLog.i(TAG, "bind failed");
                if (null == mListener) {
                    stopSelf();
                } else {
                    mListener.onPairedFailed(mRemoteDevice);
                }
                mRunningFlag = false;
            }
        }

        /**
         * 通知服务，目标设备已经绑定成功
         */
        void setBonded() {
            mBonded = true;
            BlueLog.i(TAG, "received bonded notice");
        }

        /**
         * 绑定蓝牙设备（配对），最长尝试1分钟
         */
        private void bindDevice() {
            if (mRemoteDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                BlueLog.i(TAG, "has bonded");
                mBonded = true;
                return;
            }
            try {
                if (BlueConfig.CLASSIC_AUTO_PAIRED) {
                    mRemoteDevice.getClass().getMethod("cancelPairingUserInput", byte[].class).invoke(mRemoteDevice);
                    //noinspection PrimitiveArrayArgumentToVarargsMethod
                    mRemoteDevice.getClass().getMethod("setPin", byte[].class).invoke(mRemoteDevice, BlueConfig.PIN);
                }
                mRemoteDevice.getClass().getMethod("createBond").invoke(mRemoteDevice);
            } catch (Exception e) {
                e.printStackTrace();
                BlueLog.i(TAG, "bind Exception:" + e.toString());
                return;
            }
            int limit = BlueConfig.PAIRED_WAIT_TIME_SECONDS;
            while (limit > 0 && mRunningFlag) {
                if (mBonded) {
                    return;
                }
                BlueLog.i(TAG, "bind limit" + limit);
                limit--;
                if (limit == 0) {
                    BlueLog.i(TAG, "bind failed");
                } else {
                    SystemClock.sleep(1000);
                }
            }
        }

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        BlueLog.i(TAG, "onBind");
        // 获取蓝牙地址mac
        String mac = intent.getStringExtra(BlueConfig.EXTRA_KEY_MAC);
        BlueLog.i(TAG, "remote MAC:" + mac);
        // 获取蓝牙模式，默认经典蓝牙
        int mode = intent.getIntExtra(BlueConfig.EXTRA_KEY_MODE, BlueConfig.MODE_CLASSIC);
        // 校验参数，异常返回null
        if (null == mac || !mac.matches(BlueConfig.MAC_PATTERN) || (mode != BlueConfig.MODE_CLASSIC && mode != BlueConfig.MODE_LE)) {
            return null;
        } else {
            mBinder = new BlueSocketBinder(mac, mode);
            return mBinder;
        }
    }

    @Override
    public void onDestroy() {
        BlueLog.i(TAG, "onDestroy");
        // 释放资源
        if (null != mBinder) {
            mBinder.release();
        }
        super.onDestroy();
    }
}

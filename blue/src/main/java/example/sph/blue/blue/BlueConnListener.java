package example.sph.blue.blue;

import android.bluetooth.BluetoothDevice;

/**
 * 蓝牙通道监听
 *
 * @author ShiPengHao
 * @date 2018/2/4
 */
interface BlueConnListener {
    /**
     * 连接成功
     */
    void onConnect(BluetoothDevice remoteDevice);

    /**
     * 连接中断
     */
    void onDisconnect(BluetoothDevice remoteDevice);

    /**
     * 配对失败
     */
    void onPairedFailed(BluetoothDevice remoteDevice);

    /**
     * 没有发现设备
     */
    void onDiscoveryFailed(BluetoothDevice remoteDevice);
}

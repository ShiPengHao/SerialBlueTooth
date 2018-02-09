package example.sph.blue.blue;

import android.support.annotation.IntDef;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.UUID;

/**
 * 蓝牙相关配置
 *  
 *
 * @author ShiPengHao
 * @date 2018/2/4
 */
@SuppressWarnings({"WeakerAccess"})
public class BlueConfig {
    /**
     * 模式：低功耗
     */
    public static final int MODE_LE = 1;
    /**
     * 模式：经典蓝牙
     */
    public static final int MODE_CLASSIC = 2;

    /**
     * 蓝牙模式
     */
    @IntDef({MODE_LE, MODE_CLASSIC})
    @Retention(RetentionPolicy.SOURCE)
    @Documented
    @interface Mode {
    }

    /**
     * mac地址正则
     */
    static final String MAC_PATTERN = "^[A-F0-9]{2}(:[A-F0-9]{2}){5}$";
    /**
     * 蓝牙mac的key
     */
    static final String EXTRA_KEY_MAC = "EXTRA_KEY_MAC";
    /**
     * 蓝牙模式的key
     */
    static final String EXTRA_KEY_MODE = "EXTRA_KEY_MODE";
    /**
     * 蓝牙状态码的key
     */
    static final String EXTRA_KEY_STATUS = "EXTRA_KEY_STATUS";
    /**
     * 蓝牙状态信息的key
     */
    static final String EXTRA_KEY_DES = "EXTRA_KEY_DES";
    /**
     * 经典蓝牙目标远程设备串口通讯的UUID
     */
    static final UUID UUID_CLASSIC_COM = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    /**
     * 低功耗service UUID
     */
    static final UUID UUID_LE_SERVICE = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB");
    /**
     * 低功耗characteristic UUID
     */
    static final UUID UUID_LE_CHARACTERISTIC = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB");
    /**
     * 低功耗客户端descriptor UUID
     */
    static final UUID UUID_LE_DESCRIPTOR_CLIENT = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
    /**
     * 扫描等待时间，单位秒
     */
    static final int SCAN_WAIT_TIME_SECONDS = 60;
    /**
     * 配对等待时间，单位秒
     */
    static final int PAIRED_WAIT_TIME_SECONDS = 60;
    /**
     * 读操作最大等待时间，单位毫秒
     */
    static final int READ_WAIT_TIME_MILLIS = 10000;
    /**
     * 蓝牙连接状态发生改变时，是否toast提示
     */
    static final boolean SHOW_STATE_TOAST = true;
    /**
     * 蓝牙连接状态发生改变时，是否发送进程内广播提示
     */
    static final boolean SHOW_STATE_LOCAL_BROADCAST = true;
    /**
     * 蓝牙连接状态发生改变时，是否向状态栏发生通知提示
     */
    static final boolean SHOW_STATE_NOTIFICATION = true;
    /**
     * 经典蓝牙配对时是否自动配对
     */
    static final boolean CLASSIC_AUTO_PAIRED = false;
    /**
     * 经典蓝牙LMP配对时使用的PIN码
     */
    static final byte[] PIN = new byte[]{0x01, 0x02, 0x03, 0x04};
    /**
     * 蓝牙状态通知id
     */
    static final int BLUE_NOTIFICATION_ID = 100;

}

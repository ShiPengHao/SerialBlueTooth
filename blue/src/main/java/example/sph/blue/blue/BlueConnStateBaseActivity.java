package example.sph.blue.blue;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

/**
 * 蓝牙连接状态监控Activity基类
 *  
 *
 * @author ShiPengHao
 * @date 2018/2/4
 */
public abstract class BlueConnStateBaseActivity extends Activity {

    /**
     * 蓝牙连接状态监控本地广播
     */
    private final BroadcastReceiver BLUE_STATE_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BlueManager.BLUE_STATE_ACTION.equalsIgnoreCase(intent.getAction())) {
                onStateChanged(intent.getIntExtra(BlueConfig.EXTRA_KEY_STATUS, -1), intent.getStringExtra(BlueConfig.EXTRA_KEY_DES));
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocalBroadcastManager.getInstance(this).registerReceiver(BLUE_STATE_RECEIVER, BlueManager.BLUE_STATE_INTENT_FILTER);
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(BLUE_STATE_RECEIVER);
        super.onDestroy();
    }

    /**
     * 蓝牙状态发生变化
     *
     * @param stateCode 状态码
     */
    @MainThread
    protected abstract void onStateChanged(int stateCode, String msg);

}

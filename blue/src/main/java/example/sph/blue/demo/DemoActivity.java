package example.sph.blue.demo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import example.sph.blue.R;
import example.sph.blue.blue.BlueConfig;
import example.sph.blue.blue.BlueConnStateBaseActivity;
import example.sph.blue.blue.BlueManager;

public class DemoActivity extends BlueConnStateBaseActivity {

    private BlueManager mBlueManager;
    private TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 获取单例
        mBlueManager = BlueManager.getInstance();

        tv = (TextView) findViewById(R.id.tv);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != getIntent() && "123".equalsIgnoreCase(getIntent().getAction())) {
                    // 设置模式为低功耗，默认蓝牙
                    mBlueManager.setMode(BlueConfig.MODE_LE);
                    // 设置目标设备的蓝牙地址
                    String mac = "A4:D5:78:0E:4A:0B";
                    // 开始连接
                    mBlueManager.connect(mac);
                } else {
                    startActivity(new Intent(DemoActivity.this, DemoActivity.class).setAction("123"));
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != mBlueManager) {
            if (null != getIntent() && "123".equalsIgnoreCase(getIntent().getAction())) {
                // 蓝牙操作页面，销毁时自动关闭蓝牙通道，并弹出通知。也可以随时手动关闭。
                mBlueManager.close(false, true);
            } else {
                // 主页面，销毁时关闭蓝牙，清除所有蓝牙通知。
                mBlueManager.close();
                mBlueManager.clearNotification();
            }
        }
    }

    @Override
    protected void onStateChanged(int stateCode, String msg) {
        // 展示蓝牙状态
        tv.setText(msg);
    }
}

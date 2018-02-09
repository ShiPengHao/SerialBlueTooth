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

        mBlueManager = BlueManager.getInstance();

        tv = (TextView) findViewById(R.id.tv);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != getIntent() && "123".equalsIgnoreCase(getIntent().getAction())) {
                    mBlueManager.setMode(BlueConfig.MODE_LE);
                    mBlueManager.connect("A4:D5:78:0E:4A:0B");
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
                mBlueManager.close(false, true);
            } else {
                mBlueManager.close();
                mBlueManager.clearNotification();
            }
        }
    }

    @Override
    protected void onStateChanged(int stateCode, String msg) {
        tv.setText(msg);
    }
}

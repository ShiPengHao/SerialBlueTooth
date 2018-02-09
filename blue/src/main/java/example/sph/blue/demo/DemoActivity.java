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

    private BlueManager blueManager;
    private TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        blueManager = BlueManager.getInstance();

        tv = (TextView) findViewById(R.id.tv);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != getIntent() && "123".equalsIgnoreCase(getIntent().getAction())) {
                    blueManager.setMode(BlueConfig.MODE_LE);
                    blueManager.reset("A4:D5:78:0E:4A:0B");
                } else {
                    startActivity(new Intent(DemoActivity.this, DemoActivity.class).setAction("123"));
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != blueManager) {
            if (null != getIntent() && "123".equalsIgnoreCase(getIntent().getAction())) {
                blueManager.close(false, true);
            } else {
                blueManager.close();
                blueManager.clearNotification();
            }
        }
    }

    @Override
    protected void onStateChanged(int stateCode, String msg) {
        tv.setText(msg);
    }
}

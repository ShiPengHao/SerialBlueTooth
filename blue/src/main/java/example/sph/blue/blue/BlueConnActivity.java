package example.sph.blue.blue;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.TextView;

import example.sph.blue.R;


/**
 * 蓝牙连接等待页面
 *  
 *
 * @author ShiPengHao
 * @date 2018/2/4
 */
public class BlueConnActivity extends BlueConnStateBaseActivity {
    /**
     * 提示用户取消连接的对话框
     */
    private AlertDialog mCancelDialog;
    private TextView tv;
    @SuppressWarnings("FieldCanBeLocal")
    private final String TAG = "BlueConnActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);
        tv = findViewById(R.id.tv);
        mCancelDialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.blue_ask_cancel_bt_conn))
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BlueManager.getInstance().close();
                        dialog.dismiss();
                        finish();
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create();
    }

    @Override
    protected void onDestroy() {
        if (null != mCancelDialog) {
            mCancelDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    protected void onStateChanged(int stateCode, String msg) {
        if (stateCode < 0) {
            return;
        }
        BlueLog.i(TAG, msg);
        if (stateCode < BlueManager.STATUS_SEARCHING) {
            finish();
        } else {
            tv.setText(msg);
        }
    }

    @Override
    public void onBackPressed() {
        if (mCancelDialog.isShowing()) {
            mCancelDialog.dismiss();
        } else {
            mCancelDialog.show();
        }
    }
}

package example.sph.blue.demo;

import android.app.Application;

import example.sph.blue.blue.BlueManager;
import com.squareup.leakcanary.LeakCanary;

/**
 * Application
 *  
 *
 * @author ShiPengHao
 * @date 2018/1/12
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        LeakCanary.install(this);
        // 初始化蓝牙
        BlueManager.init(this);
    }
}

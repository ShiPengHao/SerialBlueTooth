package example.sph.blue.blue;

import android.util.Log;


import java.util.Locale;

import example.sph.blue.BuildConfig;


/**
 * log日志打印控制工具类
 *  
 *
 * @author ShiPengHao
 * @date 2018/2/4
 */
public class BlueLog {
    /**
     * 是否显示线程信息
     */
    private static final boolean SHOW_THREAD = true;
    /**
     * 是否显示堆栈信息
     */
    private static final boolean SHOW_STACE = true;

    static void i(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            if (SHOW_THREAD) {
                msg = msg + "./" + Thread.currentThread().getName();
            }
            if (SHOW_STACE){
                StackTraceElement element =  new Throwable().getStackTrace()[1];
                String className = element.getFileName().substring(0, element.getFileName().lastIndexOf("."));
                msg = String.format(Locale.CHINA,"%s::%s(%s:%d)/%s", className, element.getMethodName(), element.getFileName(), element.getLineNumber(), msg);
            }
            Log.i(tag, msg);
        }
    }

}

package com.example.wanglu.wechatimageloader;

import android.os.Environment;

/**
 * Created by wanglu on 15/9/9.
 */
public class Utils {
    /**
     * 获取sdcard状态
     * @return
     */
    public static boolean isExternalEnable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }
}

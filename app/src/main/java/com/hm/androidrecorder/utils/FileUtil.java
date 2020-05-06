package com.hm.androidrecorder.utils;

import android.os.Environment;
import android.util.Log;

import com.hm.androidrecorder.constant.GlobalConfig;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;

/**
 * @author hm
 * @version [v1, 2020-04-30]
 * @Describe:
 */


public class FileUtil {

    // 获取sdcard的目�?
    public static String getSDPath()
    {
        // 判断sdcard是否存在
        if (Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED))
        {
            // 获取根目�?
            File sdDir = Environment.getExternalStorageDirectory();
            return sdDir.getPath();
        }
        return GlobalConfig.PACKAGE_NAME;
    }

    public static String createNewFile(String path)
    {

        File file = new File(path);
        File parentFile = file.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }

        if(!file.exists()) {
            try {
                if(file.createNewFile()){
                    Log.e(GlobalConfig.LOG_PROCESS,"文件创建完成:"+path);
                }else {
                    Log.e(GlobalConfig.LOG_PROCESS,"文件创建失败:"+path);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        return path;
    }


    public static String getSystemTime() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm");
        String format = simpleDateFormat.format(System.currentTimeMillis());
        return format;
    }
}

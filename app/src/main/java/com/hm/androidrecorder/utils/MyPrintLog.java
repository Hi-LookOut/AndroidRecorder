package com.hm.androidrecorder.utils;

import android.util.Log;

import com.hm.androidrecorder.constant.GlobalConfig;

/**
 * @author hm
 * @version [v1, 2020-04-27]
 * @Describe:
 */


public class MyPrintLog {
    public static void LogProcess(String printContent){
        LogProcess("",printContent);
    }
    public static void LogProcess(Class c,String printContent){
        LogProcess(c.getName(),printContent);
    }
    public static void LogProcess(Class c,String printContent,int printLineNum){
        LogProcess(c.getName(),printContent,printLineNum);
    }
    public static void LogProcess(String className,String printContent){
        Log.e(GlobalConfig.LOG_PROCESS,className+":---------"+printContent);
    }

    public static void LogProcess(String className,String printContent,int printLineNum){
        Log.e(GlobalConfig.LOG_PROCESS,className+":---------"+printContent+"----------打印行数："+printLineNum);
    }


    public static void LogErr(String errStr,Exception e){
        Log.e(GlobalConfig.LOG_ERR,errStr+"，异常："+e.toString());
    }
    public static void LogErr(String errStr,Exception e,int errLineNum){
        Log.e(GlobalConfig.LOG_ERR,errStr+"，异常："+e.toString()+"-------打印行数："+errLineNum);
    }
}

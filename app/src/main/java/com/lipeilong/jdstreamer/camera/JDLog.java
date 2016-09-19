
package com.lipeilong.jdstreamer.camera;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 日志管理
 */
public class JDLog {

    private static final String TAG = JDLog.class.getSimpleName();
    private static final boolean PRINT_LOG = true;

    /**
     * 一般log
     * 
     * @param tag
     * @param content
     */
    public static void log(String tag, String content) {
        if (tag == null || content == null) {
            return;
        }

        if (PRINT_LOG) {
            Log.d(tag, buildMessageSafe(content));
        }
    }

    public static void log(String content) {
        if (PRINT_LOG) {
            Log.d(getTag(), buildMessageSafe(content));
        }
    }
    
    public static void log(String content, Object... obj) {
        log(String.format(content, obj));
    }

    /**
     * 错误log
     * 
     * @param tag
     * @param content
     */
    public static void logError(String tag, String content) {
        if (tag == null || content == null) {
            return;
        }

        if (PRINT_LOG) {
            Log.e(tag, buildMessageSafe(content));
        }
    }

    public static void logError(String content) {
        if (content == null) {
            return;
        }

        if (PRINT_LOG) {
            Log.e(getTag(), buildMessageSafe(content));
        }
    }

    /**
     * 将日志输出到文件
     *
     * @param tag
     * @param content
     */
    private static FileOutputStream logFile = null;
    private static long mLogTime;

    public static String getLogDir(){
        return "/sdcard";
    }

    public static void logToFile(String tag, String content) {
        if (tag == null || content == null) {
            return;
        }

        if (!PRINT_LOG) {
            return;
        }

        // 创建日志文件
        if (logFile == null) {

            try {
                String path = getLogDir();
                File pathFile = new File(path);
                if (!pathFile.exists() || !pathFile.isDirectory()) {
                    pathFile.mkdirs();
                }
                String fileName = path + "/" + "log-" + getDateTime() + ".log";
                logFile = new FileOutputStream(new File(fileName));
            } catch (Exception e) {
                log(TAG, "an error occured while create log file..." + e.toString());
            }
        }

        // 写日志
        try {
            if (logFile != null) {

                String log = String.format("%s %s: %s\n", getMillTimeEx(), tag, content);
                logFile.write(log.getBytes());
                logFile.flush();
                // logFile.close();
            }
        } catch (Exception e) {
            log(TAG, "an error occured while writing log file..." + e.toString());
        }
    }

    /**
     * 辅助函数：获取当前时间
     * 
     * @return
     */
    public static String getMillTimeEx() {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        return format.format(new Date());
    }

    /**
     * 辅助函数：获取当前时间
     * 
     * @return
     */
    public static String getDateTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US);
        return format.format(new Date());
    }

    /**
     * 标记开始时间
     * PS:配套logTimeDur使用
     */
    public static void tagTimeStart() {
        mLogTime = System.currentTimeMillis();
    }
    
    /**
     * 打印标记开始到现在的时间间隔
     * PS：配套tagTimeStart使用
     */
    public static void logTimeDur(String tag) {
        log(tag, "Consume ms time:" + (System.currentTimeMillis() - mLogTime));
    }
    
    /**
     * 用来调试时间间隔
     * 
     * @param time
     */
    public static void logTime(String log, long time) {
        log("Time", log + ": " + (time - mLogTime));
        mLogTime = time;
    }

    private static String buildMessageSafe(String msg) {
        try {
            return buildMessage(msg);
        } catch (Exception e) {
        }
        return msg;
    }

    private static String buildMessage(String msg) {
        StackTraceElement[] trace = new Throwable().fillInStackTrace().getStackTrace();
        String caller = "";
        String clssName = "";
        for (int i = 3; i < trace.length; i++) {
            Class<?> clazz = trace[i].getClass();
            if (!clazz.equals(JDLog.class)) {
                clssName = trace[i].getClassName();
                clssName = clssName.substring(clssName.lastIndexOf('.') + 1);
                caller = clssName + "(L" + trace[i].getLineNumber() + ") " + trace[i].getMethodName();
                break;
            }
        }
        return String.format(Locale.US, "[%d] %s: %s", Thread.currentThread()
                .getId(), caller, msg == null ? "" : msg);
    }

    private static String getTag() {
        StackTraceElement[] trace = new Throwable().fillInStackTrace().getStackTrace();
        String clssName = "";
        for (int i = 3; i < trace.length; i++) {
            Class<?> clazz = trace[i].getClass();
            if (!clazz.equals(JDLog.class)) {
                clssName = trace[i].getClassName();
                clssName = clssName.substring(clssName.lastIndexOf('.') + 1);
                break;
            }
        }
        return clssName;
    }
    
    /**
     * 显示方法的调用轨迹，过滤关键字“showCallStack”
     */
    public static void showCallStack() {
        if (!PRINT_LOG) {
            return;
        }
        StackTraceElement[] trace = new Throwable().fillInStackTrace().getStackTrace();
        StringBuilder builder = new StringBuilder();
        String temp = "";
        for (int i = trace.length - 1; i > 0; i--) {
            temp = trace[i].getClassName();
            temp = temp.substring(temp.lastIndexOf('.') + 1);
            builder.append(temp + "." + trace[i].getMethodName() + "()(L" + trace[i].getLineNumber() + ")");
            builder.append(" ——> ");
        }
        builder.append(" ——> " + TAG + ".showCallStack()");
        Log.e(TAG, "showCallStack:" + builder.toString());
    }
}

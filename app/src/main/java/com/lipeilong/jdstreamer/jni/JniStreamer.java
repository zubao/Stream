package com.lipeilong.jdstreamer.jni;

/**
 * Created by lipeilong on 16/9/10.
 *
 * javah -jni JniStreamer.java 可以用来生成头文件或者alt + enter
 *
 * 不使用gradle 集成的编译, 自己使用命令编译
 * lipeilongdeMacBook-Pro:main lipeilong$ ndk-build APP_BUILD_SCRIPT=jni/Android.mk NDK_LIBS_OUT=libs

 */

public class JniStreamer {

    public static native String getTag();

    public static native int initial(int width, int height);

    public static native int encode(byte[] yuv);

    public static native int flush();

    public static native int close();

    static{
        System.loadLibrary("avutil-54");
        System.loadLibrary("swresample-1");
        System.loadLibrary("avcodec-56");
        System.loadLibrary("avformat-56");
        System.loadLibrary("swscale-3");
        System.loadLibrary("postproc-53");
        System.loadLibrary("avfilter-5");
        System.loadLibrary("avdevice-56");
        System.loadLibrary("JDStreamer");

    }
}

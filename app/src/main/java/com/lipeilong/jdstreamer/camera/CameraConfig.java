package com.lipeilong.jdstreamer.camera;

import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.text.TextUtils;

/**
 * 摄像头属性配置
 * 
 * @author ls
 *
 */
public class CameraConfig {
    
    private CameraConfig(int cameraType) {
        this.cameraType = cameraType; 
    }
    
    public static CameraConfig createDefaultCameraConfig(int cameraType){
        CameraConfig cameraConfig = new CameraConfig(cameraType);
        cameraConfig.initDefaultValue();
        return cameraConfig;
    }
    
    /**
     * 照相机类型（后置摄像头/前置摄像头）
     * 默认后置摄像头
     */
    int cameraType;
    
    /**
     * 默认闪光模式
     * 关闭
     */
    public String flashMode;
    
    /**
     * 默认的对焦模式
     * 
     */
    public String focusMode;
    
    /**
     * 想要的预览尺寸大小
     */
    public int preViewWidth;
    public int preViewHeight;
    
    /**
     * 真实的预览尺寸
     */
    Size preViewSize;
    
    /**
     * 照相尺寸大小
     */
    public int picWidth;
    public int picHeight;
    
    /**
     * 真实的照相尺寸大小 
     */
    Size picSize;
    
    /**
     * 视频尺寸大小
     */
    public int videoWidth;
    public int videoHeight;
    
    /**
     * 真实的视频尺寸大小
     */
    Size videoSize;
    
    /**
     * 视频文件保存路径
     */
    public String recordFilePath;
    
    /**
     * 录视频的编码比特率
     * 使10s的视频文件大小在1.2MB左右
     */
    public static final int videoEncodingBitRate = (8 * 1024 * 1024)  * 12 / 10 / 10;
    
    /**
     * 视频录制帧率
     */
    public static final int videoFrameRate   = 23;
    
    /**
     * 视频最大录制时间
     */
    public static final int videoMaxRecordMS = 10 * 1000;
    
    /**
     * 视频最短录制时间
     */
    public static final int videoMinRecordMS = 2 * 1000;
    
    /**
     * 目前对前后置摄像头初始化同样的参数
     */
    private void initDefaultValue(){

        if (TextUtils.isEmpty(flashMode)) {
            flashMode      = Camera.Parameters.FLASH_MODE_OFF;
        }
        
        if (TextUtils.isEmpty(focusMode)) {
            focusMode      = Camera.Parameters.FOCUS_MODE_AUTO;
        }
        
        if (preViewWidth == 0 || preViewHeight == 0) {
            preViewWidth    = 1280;
            preViewHeight   = 720;
        }

    }
}

package com.lipeilong.jdstreamer.camera;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 照相机类
 * 
 * 里面提供设置界面显示的接口
 * 并负责提供获和设置照相机相关的参数的接口
 * 
 * 实例化类后需要调用openCamera来打开相机,关闭相机的操作在程序切出我们进程的时候进行了
 * 
 * 注意：在调用前最好使用isCameraOpen来确认照相机是否打开，否则会调用失败
 * 使用时需要先使用setPreView函数来设置预览界面，否则不能进行后续的操作
 * 调用startPreview和startMediaRecord来进行预览和录像
 * 
 * 所有照相机操作都是异步的，在子线程处理，在主线程回调
 * 
 * PS:因为是单例的原因，HandlerThread在退出摄像头的时候，每天退出
 * 
 * 摄像机状态变迁：
 * uninit  ---  inited  ---  prepare  ---  preview
 *                              |           /
 *                              |          /
 *                              |         /
 *                              recording                              
 *         
 * @author ls
 *
 */
public class CameraHelperAsy {
    
    public static final String TAG = CameraHelperAsy.class.getSimpleName();
    
    private static CameraHelperAsy mInstance;
    
    private Camera mCamera;
    
    /**
     * 照相机相关参数信息
     */
    private CameraInfo[] mCameraInfo;
    
    // 当前使用的相机(后置/前置)
    private int mCameraType;
    
    /**
     * 当前是否是闪关灯
     */
    private boolean mIsSupportLight;

    // 使用相机的属性缓存
    private CameraConfig[] mCameraConfig;
    
    // 录像监听
    private CameraRecordListener mCameraRecordListener;
    
    //状态监听对象
    private CameraStateChangeListener mCameraStateChangeListener;
    
    // 照相机出错的监听
    private ErrorCallback mCameraErrorCallback;
    
    // 视频开始录制的时间
    private long mRecordStartTime;
    
    // 总共录制的时间，单位毫秒
    private long mRecordDuration;
    
    // 录像对象
    private MediaRecorder mMediaRecorder;
    
    // 最小的录制时间，如果太短的话会导致视频文件写入失败，从而视频播放崩溃
    private final long RECORD_MIN_INTERVAL = 1000 * 3; // 最短3秒
    
    // 预览view
    private TextureView mTextureView;
    
    // 预览view的监听
    private SurfaceTextureListener  mSurfaceTextureListener;
    
    // 照相机状态
    private CameraState mCameraState = CameraState.UNINIT;
    
    // 界面是否创建的标志
    private boolean mHasSurfaceCreate = false;
    
    // 回调的handle
    private Handler         mUIHandler;
    
    // 执行照相相关操作的线程
    private HandlerThread   mCameraHandlerThread;
    
    // 主线程切换操作线程的handle
    private Handler         mCameraHandler;
    
    // 自动对焦回调
    private AutoFocusCallback   mAutoFocusCallback ;
    
    /**
     * 预览界面拉伸模式
     * 
     * PS：暂时没有提供可设置的接口
     */
    private CameraUtil.PreViewScaleMode    mPreViewScaleMode = CameraUtil.PreViewScaleMode.WIDTHCROP;
    
    private Runnable    mRecordRunnable = new Runnable() {
        
        @Override
        public void run() {
            startMediaRecord();
        }
    };

    // 预览回调
	private PreviewCallback mPreviewCallback;
    private Display mDisplay;

    /**
     * 相机状态
     * @author ls
     *
     */
    public enum CameraState{
        /** 未初始化 */
        UNINIT,
        /** 初始化 */
        INITED,
        /** 相机预览或录像准备工作做好了 */
        PREPARED,
        /** 预览中 */
        PREVIEW,
        /** 录像中 */
        RECORDING        
    }
    
    /**
     *  标记是否是拼图模式
     *  PS：如果是拼图模式1、默认开启前置摄像头；2、调小像素即使用摄像模式的预览分辨率
     */
//    public boolean mIsJigsawMode;
    
    
    /**
     * 拍照的回调接口
     * @author ls
     *
     */
    public interface TakePictureCallback{
        
        /**
         * 拍照成功 
         * @param bm 返回图片
         */
        public void onSuccess(Bitmap bm);
        
        /**
         * 拍照失败
         * @param reason 失败原因
         */
        public void onFail(int reason);
    }
    
    /**
     * 状态监听接口
     * @author ls
     *
     */
    public interface CameraStateChangeListener{
        
        /**
         * 照相机操作结果回调
         * @author ls
         *
         */
        public enum CameraOperaResultType{
            /** 未知 */
            UNKNOW,
            
            /** 出现错误 */
            UNKNOW_ERROR,
            
            /** 聚焦成功 */
            FOCUS_SUCCESS,
            
            /** 聚焦失败 */
            FOCUS_FAIL,
            
            /** 打开相机成功 */
            OPEN_SUCCESS,
            
            /** 打开相机失败 */
            OPEN_FAIL,
            
            /** 关闭相机成功 */
            CLOSE_SUCCESS,
            
            /** 关闭相机失败 */
            CLOSE_FAIL,
            
            /** 启动预览成功 */
            START_PREVIEW_SUCCESS,
            
            /** 启动预览失败 */
            START_PREVIEW_FIAL,
            
            /** 启动录制视频成功 */
            START_RECORD_SUCCESS,
            
            /** 启动录制视频失败 */
            START_RECORD_FAIL,
            
            /** 设置闪光灯模式成功 */
            SWITCH_FLASH_MODE_SUCCESS,
            
            /** 设置闪光灯模式失败 */
            SWITCH_FLASH_MODE_FAIL,
        }
        
        /**
         * 状态变化
         * 
         * @param type 变化类型
         * TODO：变化类型暂未定义，后面如果需要细分再进行定义
         */
        public void onOperaState(CameraOperaResultType type);
        
    }
    
    /**
     * 摄像监听接口
     * @author ls
     *
     */
    public interface CameraRecordListener{
        /**
         * 开始录像
         * 注意：暂未实现
         */
        public void onStart();
        
        
        /** 用户主动停止 */
        public static final int STOP_TYPE_USER                  = 0;
        /** 录像达到最大时间限制 */
        public static final int STOP_TYPE_DURATION_REACHED      = STOP_TYPE_USER + 1;
        /** 录像达到最大文件大小限制 */
        public static final int STOP_TYPE_MAX_FILESIZE_REACHED  = STOP_TYPE_DURATION_REACHED + 1;
        /** 录制时间太短的停止 */
        public static final int STOP_TYPE_SHORT_TIME            = STOP_TYPE_MAX_FILESIZE_REACHED + 1;
        
        /**
         * 录像结束
         * 
         * @param type      取值为：STOP_TYPE_USER、STOP_TYPE_DURATION_REACHED、STOP_TYPE_MAX_FILESIZE_REACHED
         * @param fileAbsolutePath  视频文件保存完整路径
         */
        public void onStop(int type, String fileAbsolutePath);

        /** 未知原因 */
        public static final int FAILED_TYPE_UNKNOWE = 0;
        
        /**
         * 录像出错
         * 注意：暂时没有区分失败原因
         */
        public void onfailed(int errorCode);
    }
    
    /**
     * 获取照相机操作类
     * 
     * @return
     */
    public static CameraHelperAsy getInstance(){
        if (mInstance == null) {
            synchronized (TAG) {
                if (mInstance == null) {
                    mInstance = new CameraHelperAsy();
                }
            }
        }
        
        return mInstance;
    }
    
    private CameraHelperAsy(){
        
        // 初始化默认打开后置相机
        mCameraType     = CameraInfo.CAMERA_FACING_BACK;
        
        mCameraConfig   = new CameraConfig[Camera.getNumberOfCameras()];
        
        initCameraInfo();
        
        mAutoFocusCallback      = new AutoFocusCallback() {
            
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                
                callOperaResult(CameraStateChangeListener.CameraOperaResultType.FOCUS_SUCCESS);
            }
        };
        
        mSurfaceTextureListener = new SurfaceTextureListener() {
            
            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
//                JDLog.log(TAG, "onSurfaceTextureUpdated");
                
            }
            
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
//                JDLog.log(TAG, "onSurfaceTextureSizeChanged");
            }
            
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                JDLog.log(TAG, "onSurfaceTextureDestroyed");
                pauseCameraWhenViewValidAsy();
                mHasSurfaceCreate = false;
                return false;
            }
            
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                JDLog.log(TAG, "onSurfaceTextureAvailable");
                mHasSurfaceCreate = true;
                resumePreViewWhenViewValidAsy();
            }
        };
        
        mCameraErrorCallback = new ErrorCallback() {
            
            @Override
            public void onError(int error, Camera camera) {
                // 关闭相机
                closeCameraWithoutCallback();
                
                // 回调上层
                callOperaResult(CameraStateChangeListener.CameraOperaResultType.UNKNOW_ERROR);
            }
        };
        
        
        // 初始化照相机线程
        mCameraHandlerThread = new HandlerThread("camera");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());

        mUIHandler = new Handler(Looper.getMainLooper());
    }
    
    // --------------------------------------------------------------------------------------------------------------
    //                                              照相机状态修改外部调用接口
    // --------------------------------------------------------------------------------------------------------------
    
    /**
     * 设置照相机参数
     * 
     * PS：
     * 1.这个设置时异步设置
     * 2.设置后需要重启对应的相机，才有效果
     * 
     * @param cameraType
     */
    public void setCameraConfigAsy(final int cameraType, final CameraConfig cameraConfig) {
        mCameraHandler.postDelayed(new Runnable() {
            
            @Override
            public void run() {
                setCameraConfig(cameraType, cameraConfig);
            }
        }, 0);
    }
    
    /**
     * 重置照相机参数
     */
    public void resetCameraParamAsy(){
        mCameraHandler.postDelayed(new Runnable() {
            
            @Override
            public void run() {
                resetParam();
            }
        }, 0);
    }
    
    /**
     * 打开相机（异步）
     */
    public void openCameraAsy(){
        mCameraHandler.postDelayed(new Runnable() {
            
            @Override
            public void run() {
                openCamera();
            }
        }, 0);
    }
    
    /**
     * 判断是否存在打开的相机
     * @return
     */
    public boolean isCameraOpen(){
        return mCamera != null;
    }
    
    /**
     * 关闭相机
     */
    public void closeCameraAsy(){
        mCameraHandler.post(new Runnable() {
            
            @Override
            public void run() {
                closeCamera();
            }
        });
    }
    
    /**
     * 恢复预览
     * 注意：只有预览相机的View可靠了才能调用这个接口，否则会失败
     */
    public void resumePreViewWhenViewValidAsy(){
        
        if (mCameraState == CameraState.UNINIT) {
            
//            openCamera();
            openCameraAsy();
        }

        if (mCameraState  == CameraState.INITED && mHasSurfaceCreate) {
            
            // 如果照相机已经初始化过了，这surface创建好后，就可以直接进入预览了
            mCameraState = CameraState.PREPARED;
        }
        
        if (mCameraState == CameraState.PREPARED) {
            
            startPreviewAsy();
        }
    }
    
    /**
     * 暂停预览
     */
    public void pauseCameraWhenViewValidAsy(){
        
        if (mCameraState == CameraState.RECORDING) {
            
            stopMediaRecordAsy();
        } else if (mCameraState == CameraState.PREVIEW) {
            
            // 如果没在录像的话只暂停预览，要释放资源的话，得主动调用stopPreView();
            stopPreViewAsy();
        }
    }
    
    /**
     * 启动摄像头预览
     */
    public void startPreviewAsy(){
        mCameraHandler.post(new Runnable() {
            
            @Override
            public void run() {
                startPreView();
            }
        });
    }
    
    /**
     * 关闭摄像头预览
     */
    public void stopPreViewAsy() {
        mCameraHandler.post(new Runnable() {
            
            @Override
            public void run() {
                stopPreView();
            }
        });
    }
    
    /**
     * 开始录像
     */
    public void startMediaRecordAsy() {
        mCameraHandler.post(mRecordRunnable);
    }
    
    /**
     * 手动停止录像
     * @return
     */
    public void stopMediaRecordAsy() {
        
        mCameraHandler.post(new Runnable() {
            
            @Override
            public void run() {
                stopMediaRecord(CameraRecordListener.STOP_TYPE_USER);
            }
        });
    }
    
    /**
     * 设置摄像头状态变化监听对象
     * 
     * @param listener
     */
    public void setCameraStateChangeListener(CameraStateChangeListener listener) {
        mCameraStateChangeListener = listener;
    }
    
    /**
     * 是否正在摄像
     * @return
     */
    public boolean isRecording(){
        return mCameraState  == CameraState.RECORDING;
    }
    
    /**
     * 获得录像时长
     */
    public long getRecordDuration(){
        return mRecordDuration;
    }
    
    /**
     * 设置录像监听
     * @param listener
     */
    public void setCameraRecordListener(CameraRecordListener listener){
        mCameraRecordListener = listener;
    }
    
    /**
     * 是否有前置摄像头
     * @return
     */
    public boolean hasFaceCamera(){
        int numberOfCameras = Camera.getNumberOfCameras();
        if (numberOfCameras > 1) {
            return true;
        }else {
            return false;
        }
    }
    
    /**
     * 切换前后摄像头
     */
    public void switchCameraAsy(){
        mCameraHandler.post(new Runnable() {
            
            @Override
            public void run() {
                switchCamera();
            }
        });
    }
    
    /**
     * 切换前后摄像头
     */
    public void switchCameraAsy(final boolean isToBack){
        mCameraHandler.post(new Runnable() {
            
            @Override
            public void run() {
                switchCamera(isToBack);
            }
        });
    }
    
    public boolean enableLight() {
        return mIsSupportLight;
    }
    
    /**
     * 拍照
     * @param callback
     */
    public void takePictureAsy(final TakePictureCallback callback){
        mCameraHandler.post(new Runnable() {
            
            @Override
            public void run() {
                takePicture(callback);
            }
        });
    }
    
    /**
     * 设置预览view
     * @return
     */
    public void setPreView(TextureView textureView){
        mTextureView = textureView;
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }
    
    /**
     * 设置闪光灯模式
     * @param mode
     */
    public void setFlashModeAsy(final String mode){
        mCameraHandler.post(new Runnable() {
            
            @Override
            public void run() {
                setFlash(mode);
            }
        });
    }
    
    public void switchToNextFlashModeAsy(){
        mCameraHandler.post(new Runnable() {
            
            @Override
            public void run() {
                switchToNextFlashMode();
            }
        });
    }
    
    // -----------------------------------------------------end------------------------------------------------------
    
    /**
     * 在UI线程回调操作结果
     * @param cameraStateNotifyType
     */
    private void callOperaResult(final CameraStateChangeListener.CameraOperaResultType cameraStateNotifyType){
        if (mCameraStateChangeListener != null) {
            mUIHandler.post(new Runnable() {
                
                @Override
                public void run() {
                    if (mCameraStateChangeListener != null) {
                        mCameraStateChangeListener.onOperaState(cameraStateNotifyType);
                    }
                    
                    mUIHandler.removeCallbacksAndMessages(null);
                }
            });
        }
    }
    
    /**
     * 设置照相机参数
     * 
     * PS：设置后需要重启对应的相机，才有效果
     * @param cameraType
     */
    private void setCameraConfig(int cameraType, CameraConfig cameraConfig) {
        if (cameraType >= Camera.getNumberOfCameras() || cameraConfig == null) {
            return;
        }
        
        mCameraConfig[cameraType] = cameraConfig;
    }
    
    
    /**
     * 打开相机
     * @return
     */
    @SuppressLint("NewApi")
    private boolean openCamera(){
        
        long beginTime = System.currentTimeMillis();
        
        boolean result = false;
        
        if (mCamera == null) {
            
            // 重新开启摄像头
            try {
                mCamera = Camera.open(mCameraType);
                mCamera.setErrorCallback(mCameraErrorCallback);
                
                if (mCamera != null) {
                    setCameraDisplayOrientation(mCamera, mCameraInfo[mCameraType]);        // 预览角度旋转
                    
                    Parameters parameters = mCamera.getParameters();
                    
                    initCameraConfig(parameters);
                    
                    if (parameters.isZoomSupported()) {
                        parameters.setZoom(0);                          // 缩放比例
                    }
//            parameters.set("jpeg-quality", 85);               // 照片质量
                    parameters.setPictureFormat(ImageFormat.JPEG);      // 输出文件格式
//            parameters.setPreviewFrameRate(5);                // 预览帧数，有些手机不支持
                    setProperPicSize(parameters);                       // 图片大小
                    setProperPreviewSize(parameters);                   // 预览图片
                    setDefaultFlashMode(parameters);                    // 设置默认闪光灯模式
                    setDefaultFocusMode(parameters);                     // 设置默认对焦模式
                    
//                final int MIN_SUPPORT_RECORD_HINT = 14;
//                // 小米的前置照相机不支持这个参数
//                if (android.os.Build.VERSION.SDK_INT >= MIN_SUPPORT_RECORD_HINT && mCameraConfig.cameraType == CameraInfo.CAMERA_FACING_BACK) {
//                    parameters.setRecordingHint(true);  // 让照相机跟摄像之间能够更流畅的切换
//                }
//                
//                // 小米3不支持这个设置，会导致花屏
//                final int MIN_SUPPORT_VIDEO_STABILIZATION = 15;
//                if (android.os.Build.VERSION.SDK_INT >= MIN_SUPPORT_VIDEO_STABILIZATION && parameters.isVideoStabilizationSupported()) {
//                    parameters.setVideoStabilization(true);     // 防抖设置
//                }
                    mCamera.setPreviewCallback(mPreviewCallback);
                    
                    mCamera.setParameters(parameters);
                    mCameraState = CameraState.INITED;
                    result = true;
                } else {
                    JDLog.log("open camera failed");
                }
            } catch (RuntimeException e) {
                e.printStackTrace();

                JDLog.log("open camera failed");
                mCamera = null;
            }
        }
        
        final boolean openResult = result; 
        if (openResult) {
            callOperaResult(CameraStateChangeListener.CameraOperaResultType.OPEN_SUCCESS);
        } else {
            callOperaResult(CameraStateChangeListener.CameraOperaResultType.OPEN_FAIL);
        }
        
        JDLog.log(TAG, "initCamera take:" + (System.currentTimeMillis() - beginTime));
        
        return result;
    }
    
    
    /**
     * 关闭相机
     */
    private synchronized boolean closeCamera(){
        boolean result = false;
        if (mCamera != null && mCameraState != CameraState.UNINIT) {
        	// 释放之前，清除回调
        	mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
            mCameraState = CameraState.UNINIT;
            
            result = true;
        }
        
        if (result) {
            callOperaResult(CameraStateChangeListener.CameraOperaResultType.CLOSE_SUCCESS);
        } else {
            callOperaResult(CameraStateChangeListener.CameraOperaResultType.CLOSE_FAIL);
        }
        
//        mUIHandler.removeCallbacksAndMessages(null);
        return result;
    }
    
    /**
     * 在工作线程关闭相机
     * PS：不会回调上层监听
     */
    private void closeCameraWithoutCallback() {
        mCameraHandler.post(new Runnable() {
            
            @Override
            public void run() {
                if (mCamera != null && mCameraState != CameraState.UNINIT) {
                    // 释放之前，清除回调
                    mCamera.setPreviewCallback(null);
                    mCamera.release();
                    mCamera = null;
                    mCameraState = CameraState.UNINIT;
                }
            }
        });
    }
    
    /**
     * 判断照相机是否正处于预览中
     * @return
     */
    public boolean isCameraPreView(){
        return mCameraState == CameraState.PREVIEW;
    }
    
    /**
     * 启动摄像头预览
     * @return
     */
    private boolean startPreView(){
        
        long beginTime = System.currentTimeMillis();
        boolean result = false;
        
        if (mHasSurfaceCreate == true && mCameraState == CameraState.INITED) {
            mCameraState = CameraState.PREPARED;
        }
        
        if (mCamera != null && mCameraState == CameraState.PREPARED) {
            try {
                // 开始预览的时候调整下预览界面大小
                setProperPreviewSize(mCamera.getParameters());
                
                mCamera.setPreviewTexture(mTextureView.getSurfaceTexture());      // 设置预览View
                mCamera.startPreview();
                
                mCameraState = CameraState.PREVIEW;
                result = true;
            } catch (IOException e) {
                JDLog.log(TAG, "startPreView IOException");
                result = false;
                e.printStackTrace();
            } catch (Exception e) {
                JDLog.log(TAG, "startPreView OtherException");
                result = false;
                e.printStackTrace();
            }
        }
        
        if (result) {
            callOperaResult(CameraStateChangeListener.CameraOperaResultType.START_PREVIEW_SUCCESS);
        } else {
            callOperaResult(CameraStateChangeListener.CameraOperaResultType.START_PREVIEW_FIAL);
        }
        
        JDLog.log(TAG, "startPreView take:" + (System.currentTimeMillis() - beginTime));
        
        return result;
    }
    
    /**
     * 
     * 设置预览回调
     * @param callback
     */
    public void setPreviewCallbackAsy(final PreviewCallback callback) {

        mCameraHandler.post(new Runnable() {
            
            @Override
            public void run() {
                setPreviewCallback(callback);
            }
        });
    }
    
    private void setPreviewCallback(PreviewCallback callback) {
        mPreviewCallback = callback;
        
        if (mCamera != null) {
            mCamera.setPreviewCallback(mPreviewCallback);
        }
    }
    
    /**
     * 获取预览图片的格式
     * 
     * @return
     */
    public synchronized int getPreImageFormat(){ 
    	if(mCamera == null){
    		return -1;
    	}
    	return mCamera.getParameters().getPreviewFormat();
    }
    
    /**
     * 关闭摄像头预览
     * @return
     */
    private void stopPreView(){
        JDLog.log(TAG, "stopPreView");
        
        if (mCamera != null && mCameraState == CameraState.PREVIEW) {
            mCamera.stopPreview();
            mCameraState = CameraState.INITED;
        }
    }
    
    /**
     * 开始录像
     * @return
     */
    private boolean startMediaRecord(){
//        JDLog.log(TAG, "startMediaRecord");
        boolean success = false;
        
        // 检查参数
        if (mTextureView != null && mCamera != null) {

            if (mCameraState == CameraState.PREVIEW || mCameraState == CameraState.PREPARED) {
                
                if (mMediaRecorder == null){
                    mMediaRecorder = new MediaRecorder();
                }
                mMediaRecorder.reset();
                
                // 设置预览分辨率尽量跟视频分辨率一致，防止视频录制变形（注意：这个设置需在unlock之前，否则会导致崩溃）
                setProperPreviewSize(mCamera.getParameters(), mCameraConfig[mCameraType].preViewSize.width, mCameraConfig[mCameraType].preViewSize.height);
                // 注意需要先解锁，才能进行下面的操作，且不需要重新加锁，MediaRecorder.prepare()时会重新加锁
                mCamera.unlock();
                
//            if (mCameraConfig.cameraType == CameraInfo.CAMERA_FACING_FRONT) {
//                Matrix transform = new Matrix();
//                transform.reset();
//                transform.preScale(-1, 1);
//                mTextureView.setTransform(transform);
//            }
                
                
                try {
                    // ---------------------- 设置摄像参数 ----------------------
                    // 摄像源
                    mMediaRecorder.setCamera(mCamera);
                    mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    
                    // 编码参数
                    CamcorderProfile cProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P); // 分辨率720P，PS：480P会有出现变形！| 1.1版本为了界面清晰度考虑，改为480P
                    mMediaRecorder.setProfile(cProfile);
//          mMediaRecorder.setVideoFrameRate(23);  
                    mMediaRecorder.setVideoSize(mCameraConfig[mCameraType].videoSize.width, mCameraConfig[mCameraType].videoSize.height); // 使用我们定制的分辨率，不然会出现变形
                    
                    mMediaRecorder.setVideoEncodingBitRate(CameraConfig.videoEncodingBitRate); // 比特率：10s时间内2.0M的规格
                    
                    // 编码上限
                    mMediaRecorder.setMaxDuration(CameraConfig.videoMaxRecordMS);
//          mMediaRecorder.setMaxFileSize(10 * 1024 * 1024);            
                    
                    // 输出文件
                    mMediaRecorder.setOutputFile(mCameraConfig[mCameraType].recordFilePath);
                    
                    
                    // 旋转角度
                    setCameraRecordingOrientation(mMediaRecorder, mCameraInfo[mCameraType]);
                    
                    // ---------------------- 设置摄像回调 ----------------------
                    
                    
                    mMediaRecorder.setOnInfoListener(new OnInfoListener() {
                        
                        @Override
                        public void onInfo(MediaRecorder mr, int what, int extra) {
                            JDLog.log(TAG, "MediaRecorder onInfo");
                            
                            final int whatFinal = what;
                            
                            if (mCameraRecordListener != null) {
                                mUIHandler.post(new Runnable() {
                                    
                                    @Override
                                    public void run() {
                                        
                                        switch (whatFinal) {
                                            
                                            // 录制时间超过上限
                                            case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:{
                                                stopMediaRecord(CameraRecordListener.STOP_TYPE_DURATION_REACHED);
                                                break;
                                            }
                                            // 录制文件大小超过上限
                                            case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:{
                                                stopMediaRecord(CameraRecordListener.STOP_TYPE_MAX_FILESIZE_REACHED);
                                                break;
                                            }
                                            // 其他情况不处理
                                            default: {
                                                break;
                                            }
                                            
                                            
                                            
                                        }
                                    }
                                });
                            }
                            
                        }
                    });
                    
                    mMediaRecorder.setOnErrorListener(new OnErrorListener() {
                        
                        @Override
                        public void onError(MediaRecorder mr, int what, int extra) {
                            JDLog.log(TAG, "MediaRecorder onError");
                            
                            // 关闭相机
                            closeCameraWithoutCallback();
                            
                            if (mCameraRecordListener != null) {
                                mUIHandler.post(new Runnable() {
                                    
                                    @Override
                                    public void run() {
                                        mCameraRecordListener.onfailed(CameraRecordListener.FAILED_TYPE_UNKNOWE);
                                    }
                                });
                            }
                        }
                    });
                    
                
                    // ---------------------- 启动摄像 ----------------------
                    mMediaRecorder.prepare();
                    mMediaRecorder.start();
                    
                    mRecordStartTime = System.currentTimeMillis();
                    mCameraState = CameraState.RECORDING;
                    
                    // 回调上层
                    if (mCameraRecordListener != null) {
                        mUIHandler.post(new Runnable() {
                            
                            @Override
                            public void run() {
                                mCameraRecordListener.onStart();
                            }
                        });
                    }
                    
                    success = true;
                    JDLog.log(TAG, "MediaRecorder start success");
                } catch (Exception e) {
                    e.printStackTrace();
                    JDLog.log(TAG, "MediaRecorder start failed");
                }
            }
        }
        
        if (success) {
            callOperaResult(CameraStateChangeListener.CameraOperaResultType.START_RECORD_SUCCESS);
        } else {
            callOperaResult(CameraStateChangeListener.CameraOperaResultType.START_RECORD_FAIL);
        }
        
        return success;
    }
    
    private boolean stopMediaRecord(int stopType){

        JDLog.log(TAG, "stopMediaRecord");
        
        long curTime = System.currentTimeMillis();
        mRecordDuration = curTime - mRecordStartTime;

        // 录像时间太短，关闭录像会导致崩溃，所以这里测量时间，延时关闭
        boolean isRecordEnoughTime = true;
        if (mCameraState == CameraState.RECORDING && mRecordDuration < RECORD_MIN_INTERVAL) {
            isRecordEnoughTime = false;
        }
        
        boolean success = false;
        if (mCameraState == CameraState.RECORDING) {
            try {
                mMediaRecorder.stop();
                mMediaRecorder.reset();
            } catch (IllegalStateException  e) {
                e.printStackTrace();
                JDLog.logError(TAG, "stopMediaRecord IllegalStateException");
            } catch (Exception e) {
            }
            mMediaRecorder.release();
            mMediaRecorder  = null;
            
            mCameraState    = CameraState.INITED;
            success = true;
        }
        
        if (success && mCameraRecordListener != null && !TextUtils.isEmpty(mCameraConfig[mCameraType].recordFilePath)) {

            // 当用户手动录制的时间太短时，需要回调通知上层
            int stopTypeTemp = stopType;
            if ((stopTypeTemp == CameraRecordListener.STOP_TYPE_USER) && (isRecordEnoughTime == false)) {
                stopTypeTemp = CameraRecordListener.STOP_TYPE_SHORT_TIME;
            }

            // 回调通知
            final int stopTypeFinal = stopTypeTemp;
            mUIHandler.post(new Runnable() {
                
                @Override
                public void run() {
                    mCameraRecordListener.onStop(stopTypeFinal, mCameraConfig[mCameraType].recordFilePath);
                }
            });
        }
        

        mUIHandler.removeCallbacks(mRecordRunnable);
        
        return success;
    }
 
    /**
     * 切换摄像头并进入预览状态
     * @return
     */
    private boolean switchCamera() {
        // 切换摄像头为下一类型
        int cameraCount = Camera.getNumberOfCameras();
        if (cameraCount == 0) { // 出现过取不到摄像头个数的情况,兼容这种情况,直接认为只有一个摄像头,最多切换失败一次，不会导致崩溃
            cameraCount = 1;
        }
        mCameraType = (mCameraType + 1) % cameraCount;

        if (mCameraType == CameraInfo.CAMERA_FACING_BACK) {
            return switchCamera(true);
        } else {
            return switchCamera(false);
        }
    }
    
    /**
     * 切换摄像头
     * @param toBack true 切换为后置摄像头；false 切换为前置摄像头
     * @return
     */
    private boolean switchCamera(boolean toBack) {

        boolean result = false;
        if (hasFaceCamera() == false && toBack == false) {
            return result;
        }

        if (toBack) {
            mCameraType = CameraInfo.CAMERA_FACING_BACK;
            mIsSupportLight = true;
        } else {
            mCameraType = CameraInfo.CAMERA_FACING_FRONT;
            mIsSupportLight = false;
        }

        if (mCameraState == CameraState.RECORDING) {
            stopMediaRecord(CameraRecordListener.STOP_TYPE_USER);
        }

        if (mCameraState == CameraState.PREVIEW) {
            stopPreView();
        }

        if (mCameraState == CameraState.INITED || mCameraState == CameraState.PREPARED) {
            closeCamera();
        }

        if (mCameraState == CameraState.UNINIT) {
            openCamera();
            startPreView();
        }

        callOperaResult(CameraStateChangeListener.CameraOperaResultType.UNKNOW);
        return result;
    }
    
    
    
    /**
     * 进行拍照
     * 
     * @return 图片的路径
     */
    private void takePicture(final TakePictureCallback callback){
        if(mCamera != null && mCameraState  == CameraState.PREVIEW){
            PictureCallback pictureCallback = new PictureCallback() {
                
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    
                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    
                    if (bitmap != null) {
                        
                        Matrix matrix = new Matrix();
                        matrix.reset();
                        matrix.setRotate(getCameraPicOrientation(mCameraInfo[mCameraType]));        // 设置图片旋转角度
                        
                        // 前置摄像头进行左右反转
                        final boolean willDoLeftToRight = true;
                        if (willDoLeftToRight && mCameraType == CameraInfo.CAMERA_FACING_FRONT) {
                            matrix.postScale(-1, 1);
                        }
                        
                        final Bitmap bitmapResult = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                        //final String path = PathConfig.getCameraOutputPicPath();
                        
                        mUIHandler.post(new Runnable() {
                            
                            @Override
                            public void run() {
                                if (bitmapResult != null) {
                                    callback.onSuccess(bitmapResult);
                                } else {
                                    callback.onFail(0); // TODO:添加失败原因
                                }
                            }
                        });
                    }
                    
                    // 添加容错代码，这里可能会跑出异常
                    boolean result = false;
                    
                    try {
                        mCamera.stopPreview();
                        mCamera.startPreview();
                        mCameraState = CameraState.PREVIEW;
                        result = true;
                    } catch (Exception e) {
                        // TODO: handle exception
                        JDLog.log(TAG, "startPreView OtherException");
                        result = false;
                    }
                    
                    if (result) {
                        callOperaResult(CameraStateChangeListener.CameraOperaResultType.START_PREVIEW_SUCCESS);
                    } else {
                        callOperaResult(CameraStateChangeListener.CameraOperaResultType.START_PREVIEW_FIAL);
                    }
                }
            };
            
            try {
                mCamera.takePicture(new ShutterCallback() {
                    
                    @Override
                    public void onShutter() {
                        
                    }
                }, null, pictureCallback);
                
            } catch (Exception e) {
                e.printStackTrace();
                JDLog.logError(TAG, "takePicture error" + e.getMessage());
                
                mUIHandler.post(new Runnable() {
                    
                    @Override
                    public void run() {
                        callback.onFail(0); // TODO:添加失败原因
                    }
                });
            }
        } else {
            mUIHandler.post(new Runnable() {
                
                @Override
                public void run() {
                    callback.onFail(0); // TODO:添加失败原因
                }
            });
        }
    }
    
    /**
     * 获取本地照相机属性信息
     */
    private void initCameraInfo(){
        int numberOfCamera = Camera.getNumberOfCameras();
        
        if (numberOfCamera > 0) {
            mCameraInfo = new CameraInfo[numberOfCamera];
            for (int i = 0; i < numberOfCamera; i++) {
                mCameraInfo[i] = new CameraInfo();
                //这里适配CameraInfo.CAMERA_FACING_BACK定义变化的情况
                try {
                    Camera.getCameraInfo(i - CameraInfo.CAMERA_FACING_BACK, mCameraInfo[i]);
                } catch (Exception e) {
                    JDLog.log("open camera fail");
                }
            }
        }

    }
    
    /**
     * 初始化照相机配置相关
     * @param parameters
     */
    private void initCameraConfig(Parameters parameters){
        // 如果为空，则使用默认方案初始化
        CameraConfig cameraConfig = mCameraConfig[mCameraType];
        if (cameraConfig == null) {
            cameraConfig   = CameraConfig.createDefaultCameraConfig(mCameraType);
            mCameraConfig[mCameraType] = cameraConfig;
        }

        // PS：这里不再使用缓存，每次重新启动都需要判断当前模式，选取合适的size
        cameraConfig.preViewSize    = CameraUtil.getProperPreviewSize(parameters, cameraConfig.preViewWidth, cameraConfig.preViewHeight);
        cameraConfig.picSize        = CameraUtil.getProperPictureSize(parameters, cameraConfig.picWidth, cameraConfig.picHeight);
        cameraConfig.videoSize      = CameraUtil.getProperVideoSize(parameters, cameraConfig.videoWidth, cameraConfig.videoHeight);
    }
    
    /**
     * 
     * 获取预览图片的尺寸
     * @return
     */
    public Size getPreViewSize() {
        if (mCameraConfig != null) {
            CameraConfig cameraConfig = mCameraConfig[mCameraType];
            if (cameraConfig != null) {
                return cameraConfig.preViewSize;
            }
        }
        return null;
    }
    
    /**
     * 重置摄像头相关参数
     * @return
     */
    private void resetParam() {
        mCameraType     = CameraInfo.CAMERA_FACING_BACK; 
        mCameraConfig   = new CameraConfig[Camera.getNumberOfCameras()];
    }
    
    
    /**
     * 返回摄像头参数
     * @return
     */
    public int getCameraType(){
    	return mCameraType;
    }
    
    /**
     * 设置合适的预览尺寸(使用初始化的配置)
     * @param parameters
     */
    private void setProperPreviewSize(Parameters parameters) {
               
        parameters.setPreviewSize(mCameraConfig[mCameraType].preViewSize.width, mCameraConfig[mCameraType].preViewSize.height);
        
        mUIHandler.post(new Runnable() {
            
            @Override
            public void run() {
                resizePreview();
            }
        });
    }
    
    /**
     * 设置合适的预览尺寸(使用传入的参数)
     * @param parameters
     * @param needWidth         需要的预览长度
     * @param needHeight        需要的预览高度
     */
    private void setProperPreviewSize(Parameters parameters, int needWidth, int needHeight){
        
        Size properSize = CameraUtil.getProperPreviewSize(parameters, needWidth, needHeight);
        parameters.setPreviewSize(properSize.width, properSize.height);
        
        mUIHandler.post(new Runnable() {
            
            @Override
            public void run() {
                resizePreview();
            }
        });
    }
    
    /**
     * 重置预览界面大小
     */
    private void resizePreview() {
        // 参数检查
        if (mCameraConfig[mCameraType] == null) {
            return;
        }
        
        Size preViewSize = mCameraConfig[mCameraType].preViewSize;
        if (preViewSize == null) {
            return;
        }
        
        // 注意：系统返回的Size，width和height对应的界面的宽高是相反的
        float vAspectRatio  = (preViewSize.height * 1f) / (preViewSize.width * 1f);

        // 这里变化的高度基于当前给控件分配的大小
        float width  = mTextureView.getWidth();
        float height = mTextureView.getHeight();
        if (width > 0 && height > 0) {
            CameraUtil.resizePreView(mTextureView, width, height, vAspectRatio, mPreViewScaleMode);
        }
    }
    
    /**
     * 设置合适的照相尺寸
     * @param parameters
     */
    private void setProperPicSize(Parameters parameters) {
       
        parameters.setPictureSize(mCameraConfig[mCameraType].picSize.width, mCameraConfig[mCameraType].picSize.height);
    }
    
    /*-------------------------------------------------------------------------------------------*/
    /*                                      配置闪关灯属性                                       */
    /*-------------------------------------------------------------------------------------------*/
    /**
     * 是否有闪关灯
     * @return
     */
    public boolean hasFlash(String mode){ 
        
        boolean result = false;
        
        List<String> supportFlashModeList = getCurCameraSupportFlashMode();
        if (supportFlashModeList != null) {
            if (mode == null) { // 如果传入模式为空，则查是否有闪光灯
                // 拥有闪关灯的至少有两种以上闪关灯模式,off和auto模式是一定会有的
                final int MIN_MODE = 2;
                result = supportFlashModeList.size() > MIN_MODE ? true : false;
            } else {    // 查询是否存在指定的赏光灯模式
                for (String supportFlashMode : supportFlashModeList) {
                    if (supportFlashMode.equalsIgnoreCase(mode)) {
                        result = true;
                        break;
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * 获取当前摄像头支持的闪光灯类型
     * @return
     */
    public List<String> getCurCameraSupportFlashMode(){
        List<String> supportFlashMode = null;
        
        if (mCamera != null) {
            supportFlashMode = mCamera.getParameters().getSupportedFlashModes();
        }
        
        return supportFlashMode;
    }
    
    /**
     * 如果配置的闪光模式存在，则设置
     * @param parameters
     */
    private void setDefaultFlashMode(Parameters parameters){
        if (hasFlash(mCameraConfig[mCameraType].flashMode)) {
            parameters.setFlashMode(mCameraConfig[mCameraType].flashMode);
        }
        
        JDLog.log(TAG, "setFlashMode :" + mCameraConfig[mCameraType].flashMode);
    }
    
    /**
     * 设置闪关灯模式
     * @return
     */
    private boolean setFlash(String flashMode){
        boolean success = false;
        
        if (mCamera != null) {
            
            Parameters parameters = mCamera.getParameters();
            parameters.setFlashMode(flashMode);
            
            try {
                mCamera.setParameters(parameters);
                mCameraConfig[mCameraType].flashMode = flashMode;
                
                callOperaResult(CameraStateChangeListener.CameraOperaResultType.SWITCH_FLASH_MODE_SUCCESS);
                
                success = true;
            } catch (RuntimeException e) {
                e.printStackTrace();
                JDLog.logError(TAG, "setFlash RuntimeException");
                
                callOperaResult(CameraStateChangeListener.CameraOperaResultType.SWITCH_FLASH_MODE_FAIL);
            }
        }
        
        return success;
    }
    
    /**
     * 获取当前的闪光模式
     * 
     * @return  如果照相打开了，则返回当前的闪光模式，否则返回null
     */
    public String getCurFlashMode(){
        
        String curFlashMode = null;
            
        if (mCamera != null) {
            curFlashMode = mCameraConfig[mCameraType].flashMode;
        }
        
        return curFlashMode;
    }
    
    /**
     * 切换到下一种闪关灯模式
     * @return
     */
    private boolean switchToNextFlashMode(){
        boolean success = false;
        
        if (mCamera != null) {
            
            Parameters parameters = mCamera.getParameters();
            String curModeString = parameters.getFlashMode();
            
            List<String> supportModesList = parameters.getSupportedFlashModes();
            int index = supportModesList.indexOf(curModeString);
            int next = (index + 1) % supportModesList.size();
            
            String nextMode = supportModesList.get(next);
            parameters.setFlashMode(nextMode);
            
            try {
                mCamera.setParameters(parameters);
                mCameraConfig[mCameraType].flashMode = nextMode;
                callOperaResult(CameraStateChangeListener.CameraOperaResultType.SWITCH_FLASH_MODE_SUCCESS);
                
                success = true;
            } catch (RuntimeException e) {
                e.printStackTrace();
                JDLog.logError(TAG, "setFlash RuntimeException");
                callOperaResult(CameraStateChangeListener.CameraOperaResultType.SWITCH_FLASH_MODE_FAIL);
            }
        }
        
        return success;
    }
    /*---------------------------------------------end-------------------------------------------*/
    
    
    /*-------------------------------------------------------------------------------------------*/
    /*                                      聚焦相关操作                                         */
    /*-------------------------------------------------------------------------------------------*/
    /**
     * 获取支持的聚焦模式
     * @return
     */
    public List<String> getCurCameraSupportFocusMode(){
        List<String> supportFocusMode = null;
        
        if (mCamera != null) {
            supportFocusMode = mCamera.getParameters().getSupportedFocusModes();
        }
        
        return supportFocusMode;
    }
    
    /**
     * 是否有指定的对焦功能
     * @param focusMode     对焦模式，如果为null，则查看是否有对焦功能
     * @return
     */
    public boolean hasFocusFunc(String focusMode){
        boolean hasFocus = false;
        
        List<String> supportFocusModeList = getCurCameraSupportFocusMode();
        if (supportFocusModeList != null) {
            if (focusMode == null) {
                hasFocus = supportFocusModeList.size() > 0 ? true : false; 
            } else {
                for (String supportFocusMode : supportFocusModeList) {
                    if(supportFocusMode.equalsIgnoreCase(focusMode)){
                        hasFocus = true;
                        break;
                    }
                }
            }
        }
        
        return hasFocus;
    }
    
    
    /**
     * 如果配置的对焦模式存在，则设置
     * @param parameters
     */
    private void setDefaultFocusMode(Parameters parameters) {
        if (hasFocusFunc(mCameraConfig[mCameraType].focusMode)) {
            parameters.setFocusMode(mCameraConfig[mCameraType].focusMode);
        }
        
        JDLog.log(TAG, "setAutoMode :" + mCameraConfig[mCameraType].focusMode);
    }
    
    /**
     * 设置对焦模式
     * @param mode
     */
    public void setFocusModeAsy(final String mode) {
        mCameraHandler.post(new Runnable() {
            
            @Override
            public void run() {
                setFocusMode(mode);
            }
        });
    }
    
    /**
     * 设置对焦功能
     * @return
     */
    private boolean setFocusMode(String mode) {
        boolean success = false;
        
        if (mCamera != null) {
            Parameters parameters = mCamera.getParameters();
            
            parameters.setFocusMode(mode);
            
            try {
                mCamera.setParameters(parameters);
                mCameraConfig[mCameraType].focusMode = mode;
                callOperaResult(CameraStateChangeListener.CameraOperaResultType.UNKNOW);
                
                success = true;
            } catch (RuntimeException e) {
                e.printStackTrace();
                JDLog.logError(TAG, "setFocus RuntimeException");
            }
        }
        
        return success;
    }
    
    /**
     * 获取当前的对焦模式
     * 
     * @return  如果照相打开了，则返回当前的对焦模式，否则返回null
     */
    public String getCurFocusMode() {
        
        String curFocusMode = null;
        
        if (mCamera != null) {
            curFocusMode = mCameraConfig[mCameraType].focusMode;
        }
        
        return curFocusMode;
    }
    
    /**
     * 进行对焦操作
     */
    public void focusCenterAsy(){
        // 中间点聚焦
        focusOnTouch(mTextureView.getWidth() / 2,  mTextureView.getHeight() / 2);
    }
    
    /**
     * 聚焦某个点
     * @param touchX
     * @param touchY
     */
    public void focusOnTouchAsy(final float touchX, final float touchY){
        mCameraHandler.post(new Runnable() {
            
            @Override
            public void run() {
                focusOnTouch(touchX, touchY);
            }
        });
    }
      
    /**
     * 点击聚焦
     */
    private void focusOnTouch(float touchX, float touchY) {
        // 如果不支持对焦，则直接返回
        if (mCameraState != CameraState.PREVIEW || !hasFocusFunc(null)) {
            return;
        }

        if (mCamera != null && mCameraState == CameraState.PREVIEW
                && touchX < mTextureView.getWidth() && touchX > 0
                && touchY < mTextureView.getHeight() && touchY > 0) {

            Rect focusRect = calculateTapArea(touchX, touchY, 1f);
            Rect meteringRect = calculateTapArea(touchX, touchY, 0.5f);

            Parameters parameters = mCamera.getParameters();
            parameters.setFocusMode(Parameters.FOCUS_MODE_AUTO);

            if (parameters.getMaxNumFocusAreas() > 0) {

                List<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
                focusAreas.add(new Camera.Area(focusRect, 1000));

                parameters.setFocusAreas(focusAreas);
            }

            if (parameters.getMaxNumMeteringAreas() > 0) {

                List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
                meteringAreas.add(new Camera.Area(meteringRect, 1000));

                parameters.setMeteringAreas(meteringAreas);
            }

            try {
                // 聚焦的时候可能出现运行时失败
                mCamera.setParameters(parameters);
                mCamera.autoFocus(mAutoFocusCallback);
            } catch (RuntimeException e) {
                e.printStackTrace();
                JDLog.logError(TAG, "autoFocus failed");
            }
        }
    }
      
    /** 
     * 计算对焦区域
     * 坐标方位（ -1000:-1000 to 1000:1000） 
     */
    private Rect calculateTapArea(float x, float y, float coefficient) {
        // 对焦矩阵变长
        final float focusAreaSize = mTextureView.getWidth() * 0.8f;
        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();

        // 中心为点击点位置, 相对预览进行转换
        int centerX = (int) (x / mTextureView.getWidth() * 2000 - 1000);
        int centerY = (int) (y / mTextureView.getHeight() * 2000 - 1000);
        
        // 由于照相机发生了旋转，所以这里也需要旋转矫正
        int orient = getOrientation(mCameraInfo[mCameraType]);
        switch (orient) {
            case 90:{
                int temp    = centerX;
                centerX     = centerY;
                centerY     = 0 - temp;
                break;
            }
            case 180:{
                centerY     = 0 - centerY;
                centerX     = 0 - centerX;
                break;
            }
            case 270:{
                int temp    = centerX;
                centerX     = 0 - centerY;
                centerY     = temp;
                break;
            }
            default:
                break;
        }
  
        // 计算在照相机上的对焦矩阵
        int left    = clamp(centerX - areaSize / 2, -1000, 1000);
        int top     = clamp(centerY - areaSize / 2, -1000, 1000);
        int right   = clamp(left + areaSize,        -1000, 1000);
        int bottom  = clamp(top + areaSize,         -1000, 1000);
        
        return new Rect(left, top, right, bottom);
    }  

    /**
     *  调整输入处于取值范围
     * 
     * @param x
     * @param min
     * @param max
     * @return
     */
    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    /*---------------------------------------------end-------------------------------------------*/
    
    /*-------------------------------------------------------------------------------------------*/
    /*                                      相机旋转角度相关操作                                 */
    /*-------------------------------------------------------------------------------------------*/
    /**
     * 设置预览的角度
     * @param camera
     */
    private void setCameraDisplayOrientation(Camera camera, CameraInfo info){
        camera.setDisplayOrientation(getOrientation(info));
    }
    
    /**
     * 获取当前相机照片的选择角度
     * 
     * @return
     */
    public int getCameraPicOrientation() {
        CameraInfo info = mCameraInfo[mCameraType];
        return getCameraPicOrientation(info);
    }
    
    /**
     * 获取相机照片旋转角度
     */
    private int getCameraPicOrientation(CameraInfo info){
        
        int backOrientation = info.orientation;
        
        // 对于魅族MX手机进行特殊处理，它的前置摄像头需要倒置
        if (DeviceUtil.getModel().equalsIgnoreCase("M031") && info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            backOrientation = (backOrientation + 180) % 360;

        }

        return backOrientation;
    }
    
    /**
     * 设置摄像头录像文件方向信息
     */
    private void setCameraRecordingOrientation(MediaRecorder mediaRecorder, CameraInfo info){
        try {
            mediaRecorder.setOrientationHint(info.orientation);
        } catch (IllegalArgumentException  e) {
            e.printStackTrace();
            JDLog.logError(TAG, "setCameraRecordingOrientation IllegalArgumentException");
        }
    }
    
    /**
     * 预览界面拉伸模式
     */
    public void setPreViewScaleMode(CameraUtil.PreViewScaleMode preViewScaleMode){
        mPreViewScaleMode = preViewScaleMode;
    }

    public void setDisplay(Display display){
        mDisplay = display;
    }
    
    /**
     * 获取照相机旋转角度
     * @param info
     * @return
     */
    private int getOrientation(CameraInfo info){
        int rotation = mDisplay.getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        
        return result;
    }
    /*---------------------------------------------end-------------------------------------------*/
}

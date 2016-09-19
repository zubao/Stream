package com.lipeilong.jdstreamer.camera;


import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import com.lipeilong.jdstreamer.jni.JniStreamer;

import java.util.List;



/**
 * Created by lipeilong on 16/9/12.
 */

public class VideoRecordDevice {

    private JniStreamer mJniStreamer;

    private static class VideoFrameCallback implements Camera.PreviewCallback{

        @Override
        public void onPreviewFrame(byte[] bytes, Camera camera) {
            Log.d(TAG, "format: "+camera.getParameters().getPreviewFormat()+" "+bytes.length);
            handlerFrameData(bytes);
            camera.addCallbackBuffer(bytes);
        }

        private void handlerFrameData(byte[] data){
            JniStreamer.encode(data);
        }
    }

    private static final String TAG = VideoRecordDevice.class.getSimpleName();

    private Context mContext;
    private boolean mStarted;
    private Camera  mCamera;
    private SurfaceHolder       mSurfaceHolder;
    private VideoFrameCallback  mCallback;

    public VideoRecordDevice(Context context){

        mContext = context;
        mStarted = false;
    }

    private void init(){
        mJniStreamer = new JniStreamer();
    }

    public void setSurfaceHolder(SurfaceHolder holder){
        mSurfaceHolder  = holder;

        JniStreamer.initial(1280, 720);


    }


    public boolean startCamera(){
        if(mStarted){
            return true;
        }

        try{
            if(mCamera == null){
                mCamera     = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
                Camera.Parameters   parameters  = mCamera.getParameters();
                initParameters(parameters);
                mCamera.setParameters(parameters);
            }

            if(mSurfaceHolder != null){
                mCamera.setPreviewDisplay(mSurfaceHolder);
                mStarted    = startPreView();

                return mStarted;
            }
        } catch (Exception e){
            e.printStackTrace();
            mStarted = false;
            return false;
        }

        return true;
    }

    private boolean startPreView(){
        if(mCamera == null){
            return false;
        }

        try{
            if(mCallback == null){
                mCallback = new VideoFrameCallback();
            }
            mCamera.setPreviewCallbackWithBuffer(mCallback);
            for(int i=0; i<3; i++){
                mCamera.addCallbackBuffer(new byte[1280* 720 * 3 / 2]);
            }
            mCamera.startPreview();
        }catch(Exception e){
            return false;
        }

        return true;
    }

    private void initFpsParameters(Camera.Parameters parameters){
        List<int[]> list    = parameters.getSupportedPreviewFpsRange();

//        parameters.setPreviewFpsRange(20, 30);
    }

    private void initParameters(Camera.Parameters parameters){
        initPreViewSizes(parameters);
        initDisplayOrientation(parameters);
    }

    private void initFlashMode(Camera.Parameters parameters){
        List list   = parameters.getSupportedFlashModes();
        if(list != null){
//            parameters.setFlashMode();
        }
    }

    private void initWhiteBalance(Camera.Parameters parameters){
        List list   = parameters.getSupportedWhiteBalance();
    }

    private void initPreViewSizes(Camera.Parameters parameters){
        List<Camera.Size> list   = parameters.getSupportedPreviewSizes();
        Camera.Size preSize = null;
        for(Camera.Size size : list){
            if(size.width * size.height == 1280 * 720){
                preSize = size;
                break;
            }
        }

        if(preSize != null){
            Log.d(TAG, "presize "+ preSize.toString());
            parameters.setPreviewSize(preSize.width, preSize.height);
        }
    }

    private void initDisplayOrientation(Camera.Parameters parameters){
        int orientation = getOrientation(mContext, 0);
        mCamera.setDisplayOrientation(orientation);
    }

    private int getOrientation(Context context, int type){
        int rotation    = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        int degrees     =0;
        switch (rotation){
            case Surface.ROTATION_0:
                degrees     = 0;
                break;
            case Surface.ROTATION_90:
                degrees     = 90;
                break;
            case Surface.ROTATION_180:
                degrees     = 180;
                break;
            case Surface.ROTATION_270:
                degrees     = 270;
                break;

        }

        int result;
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info);
        if(info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT){
            result  = (info.orientation + degrees) % 360;
            result  = (360 - result) % 360;
        }else{
            result  = (info.orientation - degrees + 360) % 360;
        }

        return  result;
    }


}

package com.lipeilong.jdstreamer.camera;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.util.Arrays;
import java.util.List;

/**
 * Created by lipeilong on 16/9/13.
 */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraNew implements ICamera {

    CameraCaptureSession.StateCallback mCameraCaptureSessionStateCallback =  new CameraCaptureSession.StateCallback(){

        @Override
        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
            mSession    = cameraCaptureSession;

            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            try {
                mSession.setRepeatingRequest(mPreviewBuilder.build(), mCameraCaptureSessionCaptureCallback, mHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {

        }
    };

    CameraCaptureSession.CaptureCallback mCameraCaptureSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            mSession    = session;
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            mSession    = session;
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
            mSession    = session;
        }
    };

    private final CameraManager mCameraManager;
    private final Context mContext;
    private final Handler mHandler;
    private CameraDevice mCameraDevice;
    private SurfaceHolder mSurfaceHolder;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mSession;

    public CameraNew(Context context) {
        mContext = context;
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        HandlerThread handlerThread     = new HandlerThread("Camera2");
        handlerThread.start();
        mHandler    = new Handler(handlerThread.getLooper());

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public String[] getCameraIds() {
        try {
            return mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean checkCameraPermission(Activity activity) {
        boolean permission = ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        if (!permission) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, 1);

        }
        return permission;
    }



    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void setSurfaceHolder(SurfaceHolder holder){
        mSurfaceHolder  = holder;

        mImageReader    = ImageReader.newInstance(holder.getSurfaceFrame().width(), holder.getSurfaceFrame().height(), ImageFormat.JPEG, 7);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {

            }
        }, mHandler);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public ICamera open(Activity activity, int index) {
        String[] cameraIds = getCameraIds();
        if(cameraIds == null || cameraIds.length <= index){
            return null;
        }
        String cameraId     = cameraIds[index];

        try {
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return null;
            }


            mCameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cameraDevice) {
                    mCameraDevice = cameraDevice;
                }

                @Override
                public void onDisconnected(CameraDevice cameraDevice) {
                    mCameraDevice = cameraDevice;
                }

                @Override
                public void onError(CameraDevice cameraDevice, int i) {
                    mCameraDevice = cameraDevice;
                }
            }, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return this;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCameraCaptureSession()throws CameraAccessException{
        mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        mPreviewBuilder.addTarget(mSurfaceHolder.getSurface());
        List<Surface> list   = Arrays.asList(mSurfaceHolder.getSurface(), mImageReader.getSurface());
        mCameraDevice.createCaptureSession(list, new CameraCaptureSession.StateCallback(){

            @Override
            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                mSession    = cameraCaptureSession;

                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                try {
                    mSession.setRepeatingRequest(mPreviewBuilder.build(), mCameraCaptureSessionCaptureCallback, mHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {

            }
        }, mHandler);


    }
}

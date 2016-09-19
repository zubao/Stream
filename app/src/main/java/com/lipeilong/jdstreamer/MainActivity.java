package com.lipeilong.jdstreamer;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import com.lipeilong.jdstreamer.camera.VideoRecordDevice;
import com.lipeilong.jdstreamer.jni.JniStreamer;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback{

    private VideoRecordDevice mVideoRecordDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surface);
        surfaceView.getHolder().addCallback(this);
        mVideoRecordDevice = new VideoRecordDevice(getApplicationContext());
//        mVideoRecordDevice.setSurfaceHolder(surfaceView.getHolder());
    }

    public void onClick(View view){

        Toast.makeText(this, new JniStreamer().getTag(), Toast.LENGTH_LONG).show();

        mVideoRecordDevice.startCamera();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        if(surfaceHolder != null){

            mVideoRecordDevice.setSurfaceHolder(surfaceHolder);
        }
        Log.d("stream", "surfaceCreated");
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }
}

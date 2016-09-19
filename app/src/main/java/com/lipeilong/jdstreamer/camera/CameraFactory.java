package com.lipeilong.jdstreamer.camera;

import android.content.Context;
import android.os.Build;

/**
 * Created by lipeilong on 16/9/13.
 */

public class CameraFactory {

    public static ICamera create(Context context){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            return new CameraNew(context);
        }else {
            return null;
        }
    }
}

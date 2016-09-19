package com.lipeilong.jdstreamer.camera;

import android.app.Activity;

/**
 * Created by lipeilong on 16/9/13.
 */

public interface ICamera {

    public ICamera open(Activity activity, int index);

    public boolean checkCameraPermission(Activity activity);
}

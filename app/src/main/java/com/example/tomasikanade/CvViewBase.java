package com.example.tomasikanade;

import android.content.Context;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import org.opencv.videoio.VideoCapture;

public class CvViewBase extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "Sample::SurfaceView";

    private SurfaceHolder mHolder;
    private VideoCapture mCamera;

    public CvViewBase(Context context) {
        super(context);
        mHolder = getHolder();
        mHolder.addCallback(this);
        Log.i(TAG, "Instantiated new " + this.getClass());
    }


    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {

    }
}

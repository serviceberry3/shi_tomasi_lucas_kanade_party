package com.example.tomasikanade;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.SurfaceHolder;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

public abstract class BridgeView extends JavaCameraView implements Runnable {
    private static final String TAG = "BridgeView";

    //SurfaceHolder interfaces enable apps to edit and control surfaces.
    //A SurfaceHolder is an interface the system uses to share ownership of surfaces with apps. Some clients that
    //work with surfaces want a SurfaceHolder, because APIs to get and set surface parameters are implemented through a
    //SurfaceHolder. **A SurfaceView contains a SurfaceHolder**.
    //Most components that interact with a view involve a SurfaceHolder.
    private SurfaceHolder mHolder;

    private Mat sceneColor, sceneGrayscale;

    public BridgeView(Context context, int cameraId, Mat color, Mat grayscale) {
        super(context, cameraId);

        //call SurfaceView.getHolder() to get the SurfaceHolder providing access and control over this SurfaceView's underlying surface.
        mHolder = getHolder();

        //add a Callback interface for this holder (the callback is this class itself since it IMPLEMENTED SHolder.Callback
        mHolder.addCallback(this);

        //the two matrices for image data
        sceneColor = color;
        sceneGrayscale = grayscale;

        //starting log message
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /*
    @Override
    protected boolean connectCamera(int width, int height) {
        return false;
    }

    @Override
    protected void disconnectCamera() {
    }
     */

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        super.surfaceCreated(holder);
    }

    //this abstract method does the application specific logic. It's called from run()
    protected abstract Bitmap processFrame(VideoCapture capture);


    @Override
    public void run() {

    }
}

package com.example.tomasikanade;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

import java.util.List;

//extend SurfaceView, which is our drawing surface
public abstract class CvViewBase extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final String TAG = "CvViewBase";

    // SurfaceHolder interfaces enable apps to edit and control surfaces.
    //A SurfaceHolder is an interface the system uses to share ownership of surfaces with apps. Some clients that
    // work with surfaces want a SurfaceHolder, because APIs to get and set surface parameters are implemented through a
    // SurfaceHolder. **A SurfaceView contains a SurfaceHolder**.
    //Most components that interact with a view involve a SurfaceHolder.
    private SurfaceHolder mHolder;

    //videocapture is an OpenCV object used to grab camera frames
    private VideoCapture mCamera;

    private Mat sceneColor, sceneGrayscale;


    public CvViewBase(Context context, VideoCapture camera, Mat color, Mat grayscale) {
        super(context);

        //call SurfaceView.getHolder() to get the SurfaceHolder providing access and control over this SurfaceView's underlying surface.
        mHolder = getHolder();

        //add a Callback interface for this holder (the callback is this class itself since it IMPLEMENTED SHolder.Callback
        mHolder.addCallback(this);

        //our VideoCapture object
        mCamera = camera;

        //the two matrices for image data
        sceneColor = color;
        sceneGrayscale = grayscale;

        //starting log message
        Log.i(TAG, "Instantiated new " + this.getClass());
    }


    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        Log.i(TAG, "surfaceCreated");

        //index is apparently the ID of the video capturing device to open?
        //mCamera = new VideoCapture(1000);

        if (mCamera!=null) {
            if (mCamera.isOpened()) {
                Log.d(TAG, "Starting frame processing runnable on background thread");

                //THIS is a runnable, start it on a new thread
                (new Thread(this)).start();
            }

            //if the camera's not opened, release its resources. Something went wrong?
            else {
                mCamera.release();
                mCamera = null;
                Log.e(TAG, "Failed to open native camera, released VideoCapture resources");
            }
        }

        else {
            Log.e(TAG, "surfaceCreated(): the VideoHolder came up null");
        }
    }

    //Called when surface changes (like, when you flip from portrait to landscape). Compares the dimensions of the old and
    // new views and adjusts to the new conditions
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP) //should only be called on lollipop or higher
    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int format, int width, int height) {
        Log.i(TAG, "surfaceCreated");

        //lock on this object
        synchronized (this) {
            if (mCamera != null && mCamera.isOpened()) {
                /*
                Log.i(TAG, "running mCamera.getSupportedPreviewSizes()...");

                //deprecated
                List<Size> sizes = mCamera.getSupportedPreviewSizes;

                Log.i(TAG, "completed mCamera.getSupportedPreviewSizes()");

                int mFrameWidth = width;
                int mFrameHeight = height;

                // selecting optimal camera preview size
                {
                    double minDiff = Double.MAX_VALUE;
                    for (Size size : sizes) {
                        int thisWidth = size.getWidth(), thisHeight = size.getHeight();

                        if (Math.abs(thisHeight - height) < minDiff) {
                            mFrameWidth = thisWidth;
                            mFrameHeight = thisHeight;
                            minDiff = Math.abs(thisHeight - height);
                        }
                    }
                }
                 */

                mCamera.set(3, 1080); //3 = CV_CAP_PROP_FRAME_WIDTH
                mCamera.set(4, 1000); //4 =  CV_CAP_PROP_FRAME_HEIGHT
            }
        }
    }

    //release VideoCapture resources when SurfaceView is no longer used
    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        Log.i(TAG, "surfaceDestroyed");
        if (mCamera != null) {

            //locked on this instance of CvViewBase
            synchronized (this) {
                //release camera resources
                mCamera.release();
                mCamera = null;
            }
        }
    }

    //this abstract method does the application specific logic. It's called from run()
    protected abstract Bitmap processFrame(VideoCapture capture);

    @Override
    public void run() {
        Log.i(TAG, "Starting processing thread");

        //run this main program loop infinitely on Camera preview thread until either grabbing the camera preview fails
        //OR the VideoCapture comes up null
        while (true) {
            Bitmap bmp = null;

            synchronized (this) {
                if (mCamera == null)
                    break;

                //grab the image from the camera
                if (!mCamera.grab()) {
                    Log.e(TAG, "mCamera.grab() failed");
                    break;
                }

                //get a new filled bitmap from a run of processFrame called on our instance of VideoCapture
                bmp = processFrame(mCamera);
            }

            if (bmp != null) {
                //lock canvas from our SurfaceHolder to draw on
                //Start editing the pixels in the surface. Returned Canvas can be used to draw into the surface's bitmap. null is returned
                //if surface has not been created or can't be edited. Usually need to implement surfaceCreated()
                //to find out when the Surface is available for use.
                //Content of the Surface is never preserved between unlockCanvas() and lockCanvas(), so every pixel within the Surface area
                //must be written. Only exception to this rule is when a dirty rectangle is specified, in which case, non-dirty
                //pixels will be preserved
                //
                //If you call this repeatedly when Surface not ready (before surfaceCreated/after surfaceDestroyed), calls will be throttled to
                //slow rate in order to avoid consuming CPU
                //
                //If null is not returned, this function internally holds a lock until the corresponding unlockCanvasAndPost(Canvas) call,
                // preventing SurfaceView from creating, destroying, or modifying surface while being drawn. This can be more convenient
                //than accessing Surface directly since don't need to do special synchronization with a drawing thread in surfaceDestroyed.
                Canvas canvas = mHolder.lockCanvas();

                //null check on the canvas. if all good then draw the camera preview bitmap and push to screen
                if (canvas != null) {
                    canvas.drawBitmap(bmp, (canvas.getWidth() - bmp.getWidth()) / 2f, (canvas.getHeight() - bmp.getHeight()) / 2f, null);
                    mHolder.unlockCanvasAndPost(canvas);
                }

                //release bitmap resources after it's been copied to screen
                bmp.recycle();
            }
            //if the bitmap comes back null don't draw anything
        }

        //the processing thread is ending now
        Log.i(TAG, "Finishing processing thread");
    }
}
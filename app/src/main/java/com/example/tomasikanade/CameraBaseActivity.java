package com.example.tomasikanade;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;
import org.opencv.videoio.VideoCapture;

import java.util.List;

public class CameraBaseActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, View.OnTouchListener {

    private static final String TAG = "CameraBaseActivity";

    private VideoCapture mCamera;

    //Mat class represents an n-dimensional dense numerical single-channel or multi-channel array. Can be used to store real or
    //complex-valued vectors and matrices, grayscale or color images, voxel volumes, vector fields, point clouds, tensors, histograms

    //use 2 Mats to store the camera image, one in color (RGB), and one black and white
    private Mat sceneGrayScale, sceneColor, mPrevGray;


    private CameraBridgeViewBase mOpenCvCameraView;

    //Values needed for the corner detection algorithm Most likely have to tweak them to suit needs. Could also
    //let the application find out the best values by itself.
    private final static double qualityLevel = 0.35;
    private final static double minDistance = 10;
    private final static int blockSize = 8;
    private final static boolean useHarrisDetector = false;
    private final double k = 0.0;
    private final static int maxCorners = 100;
    private final static Scalar circleColor = new Scalar(255, 255, 0);


    public CameraBaseActivity() {
        //starting log message
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called");
        super.onCreate(savedInstanceState);

        //remove the title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        //make sure screen stays on even if it's not touched for a while
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //setContentView(R.layout.camera_view);
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            Log.i(TAG, "BaseLoaderCallback called!");
            if (status == LoaderCallbackInterface.SUCCESS) {//instantiate everything we need from OpenCV
                mCamera = new VideoCapture();
                sceneColor = new Mat();
                sceneGrayScale = new Mat();

                //everything succeeded
                Log.i(TAG, "OpenCV loaded successfully, everything created");


                mOpenCvCameraView = new ShiTomasiView(CameraBaseActivity.this, 0);


                mOpenCvCameraView.setCvCameraViewListener(CameraBaseActivity.this);
                mOpenCvCameraView.setOnTouchListener(CameraBaseActivity.this);


                mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
                mOpenCvCameraView.enableView();

                //SurfaceHolder interfaces enable apps to edit and control surfaces.
                //A SurfaceHolder is an interface the system uses to share ownership of surfaces with apps. Some clients that
                //work with surfaces want a SurfaceHolder, because APIs to get and set surface parameters are implemented through a
                //SurfaceHolder. **A SurfaceView contains a SurfaceHolder**.
                //Most components that interact with a view involve a SurfaceHolder.
                SurfaceHolder mHolder = mOpenCvCameraView.getHolder();

                //display the new instance of ShiTomasi view
                setContentView(mOpenCvCameraView);
            }

            else {
                super.onManagerConnected(status);
            }
        }
    };

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume called");
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        }

        else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        synchronized (this) {
            // Explicitly deallocate Mats
            if (sceneColor != null) {
                sceneColor.release();
            }
            if (sceneGrayScale != null) {
                sceneGrayScale.release();
            }

            sceneColor = null;
            sceneGrayScale = null;
        }
    }


    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return false;
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        sceneColor = inputFrame.rgba();

        //convert the color image matrix to a grayscale one to improve memory usage and processing time (1 bpp instead of 3)
        //also converting to grayscale makes the contrast between features clearer
        Imgproc.cvtColor(sceneColor, sceneGrayScale, Imgproc.COLOR_BGRA2GRAY);

        //a matrix of points to store the corner points in when Shi-Tomasi runs
        MatOfPoint corners = new MatOfPoint();

        /* run Shi-Tomasi
        @param sceneGrayScale - our image that we want to detect corners in
        @param corners is our list of corners found by the algorithm.
        @param maxCorners is the maximum number of corners we want it to return.
        @param qualityLevel is the minimum ”quality level” of the results found for the result to be considered a corner.
        @param minDistance is the minimum distance in pixels required from one corner to the next.

        @param mat a mask in case we want to focus on a certain area of the image.
        @param blockSize is how big an area, in pixels, the algorithm will use to define corners.
        @param boolean is whether we're going to use Harris Corner Detection or not. In this example we aren't, we use Shi-Tomasi
        @param k value, only used in Harris Corner Detection.
         */
        Imgproc.goodFeaturesToTrack(sceneGrayScale, corners, maxCorners, qualityLevel, minDistance, new Mat(), blockSize, useHarrisDetector, k);

        //get array of points from corners (filled in by the algorithm)
        Point[] points = corners.toArray();

        //Log.i(TAG, String.format("Found %d points to draw", points.length));
        for (Point p : points) {
            //what is core?
            Imgproc.circle(sceneGrayScale, p, 2, circleColor, 10);
        }


        //Log.i(TAG, "onCameraFrame() returning grayScale matrix...");
        return sceneGrayScale;
    }

    //This is a Lucas-Kanade processor for a given Mat
    public Mat sparseFlow(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat mGray = inputFrame.gray();
        Mat mGrayT = mGray.t();
        Core.flip(mGray.t(), mGrayT, 1);
        Imgproc.resize(mGrayT, mGrayT, mGray.size());
        double xAvg1 = 0;
        double xAvg2 = 0;
        double yAvg1 = 0;
        double yAvg2 = 0;

        if (features.toArray().length == 0) {
            int rowStep = 50, colStep = 100;

            //create a new array of 12 points
            Point points[] = new Point[12];

            int k = 0;
            for (int i = 3; i <= 6; i++) {
                for (int j = 2; j <= 4; j++) {
                    points[k] = new Point(j * colStep, i * rowStep);
                    k++;
                }
            }

            features.fromArray(points);

            prevFeatures.fromList(features.toList());
            mPrevGray = mGrayT.clone();
        }

        nextFeatures.fromArray(prevFeatures.toArray());

        Video.calcOpticalFlowPyrLK(mPrevGray, mGrayT, prevFeatures, nextFeatures, status, err
        );

        List<Point> prevList = features.toList(), nextList = nextFeatures.toList();
        Scalar color = new Scalar(255, 0, 0);
        int listSize = prevList.size();

        for (int i = 0; i < listSize; i++) {
            if (prevList.get(i) != null) {
                xAvg1 += prevList.get(i).x;
                yAvg1 += prevList.get(i).y;
            }
            if (nextList.get(i) != null) {
                xAvg2 += nextList.get(i).x;
                yAvg2 += nextList.get(i).y;
            }
            Imgproc.line(mGrayT, prevList.get(i), nextList.get(i), color);
        }

        xAvg1 /= listSize;
        xAvg2 /= listSize;
        yAvg1 /= listSize;
        yAvg2 /= listSize;
        double pointX = xAvg1 - xAvg2;
        double pointY = yAvg1 - yAvg2;

        if (points.isEmpty()) {
            points.add(new Point(pointX, pointY));
        }

        else {
            Point lastPoint = points.get(points.size() - 1);
            pointX += lastPoint.x;
            pointY += lastPoint.y;
            points.add(new Point(pointX, pointY));
        }

        //hold onto this Mat because we'll use it as the previous frame to calc optical flow next time
        mPrevGray = mGrayT.clone();

        //return the final Mat
        return mGrayT;
    }
}
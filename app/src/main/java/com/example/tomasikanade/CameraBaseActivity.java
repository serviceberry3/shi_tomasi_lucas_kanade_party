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
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;
import org.opencv.videoio.VideoCapture;

import java.util.ArrayList;
import java.util.List;

public class CameraBaseActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, View.OnTouchListener {

    private static final String TAG = "CameraBaseActivity";

    private VideoCapture mCamera;

    //Mat class represents an n-dimensional dense numerical single-channel or multi-channel array. Can be used to store real or
    //complex-valued vectors and matrices, grayscale or color images, voxel volumes, vector fields, point clouds, tensors, histograms

    //use 2 Mats to store the camera image, one in color (RGB), and one black and white
    private Mat sceneGrayScale, sceneColor, mGray, mPrevGray, image;


    private CameraBridgeViewBase mOpenCvCameraView;

    //Values needed for the corner detection algorithm Most likely have to tweak them to suit needs. Could also
    //let the application find out the best values by itself.
    private final static double qualityLevel = 0.35; //.35
    private final static double minDistance = 10;
    private final static int blockSize = 8;
    private final static boolean useHarrisDetector = false;
    private final double k = 0.0;
    private final static int maxCorners = 100;
    private final static Scalar circleColor = new Scalar(255, 255, 0);

    //initialize two matrices of points
    MatOfPoint2f prevFeatures, nextFeatures;
    MatOfPoint features;

    MatOfByte status;
    MatOfFloat err;
    List<Point> points;

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
        mGray = new Mat(height, width, CvType.CV_8UC1);
        resetVars();
    }

    @Override
    public void onCameraViewStopped() {

    }

    private void resetVars() {
        //curImage = new Mat();
        //prevImage = new Mat();
        //flowMat = new Mat();

        mPrevGray = new Mat(mGray.rows(), mGray.cols(), CvType.CV_8UC1);
        image = new Mat(4000, 4000, CvType.CV_8UC1);
        features = new MatOfPoint();
        prevFeatures = new MatOfPoint2f();
        points = new ArrayList<>();
        nextFeatures = new MatOfPoint2f();
        status = new MatOfByte();
        err = new MatOfFloat();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        sceneColor = inputFrame.rgba();

        //convert the color image matrix to a grayscale one to improve memory usage and processing time (1 bpp instead of 3)
        //also converting to grayscale makes the contrast between features clearer
        Imgproc.cvtColor(sceneColor, sceneGrayScale, Imgproc.COLOR_BGRA2GRAY);

        Point[] goodFeatures = getCorners(sceneGrayScale);

        return sparseFlow(sceneGrayScale, goodFeatures);
        //return sceneGrayScale;
    }


    //use Shi-Tomasi algorithm to get key features of the image
    Point[] getCorners(Mat grayScale) {
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


        //draw all of the key points on the screen
        for (Point p : points) {
            //what is core?
            Imgproc.circle(sceneGrayScale, p, 2, circleColor, 10);
        }



        return points;
    }


    //This is a Lucas-Kanade processor for a given Mat
    public Mat sparseFlow(Mat inputFrame, Point[] pointsToTrack) {
        //get the grayscale Mat from the input camera frame
        mGray = inputFrame;

        //do transposition
        //Mat mGrayT = mGray.t();

        //flip a 2D array around vertical, horizontal, or both axes
        //in this case we flip the mGrayT array around the y-axis, where
        // dst[i][j] = src[src.rows-i-1],[src.columns-j-1] so the top left slot of dest matrix = bottom rt of source matrix, etc.
        //so flipcode < 0 can be used, for example, for simultaneous horizontal and vertical flipping of an image w/ the subsequent shift
        // and absolute difference calculation to check for a central symmetry
        //Core.flip(mGray.t() /*isn't this just the same as saying mGrayT?*/, mGrayT, 1); //flipcode 1 means flip around y-axis

        //resize mGrayT image, the output image size being set to mGray.size()
        //Imgproc.resize(mGrayT, mGrayT, mGray.size());

        //initialize some doubles
        double xAvg1 = 0;
        double xAvg2 = 0;
        double yAvg1 = 0;
        double yAvg2 = 0;

        //if features is empty, that means we don't have any points to work with yet
        if (features.toArray().length == 0) {
            /*
            int rowStep = 50, colStep = 100;

            //create a new array of 12 Points (an x and a y)
            Point points[] = new Point[12];

            //create the points
            int k = 0;

            //what's the significance of these points? Are they just random location to track?
            for (int i = 3; i <= 6; i++) {
                for (int j = 2; j <= 4; j++) {
                    //create a point with x value of current j*100 and y value current i*50
                    //will result in (200, 150), (300, 150), (400, 150), (200, 200), (300, 200), (400, 200), (200, 250), (300, 250), ...
                    points[k] = new Point(j * colStep, i * rowStep);
                    k++;
                }
            }

             */

            //the MatofPoint class in OpenCV is a 2D array of points. We can add all of our Shi-Tomasi points into our MatofPoint instance
            //by calling fromArray() on the array of Points
            features.fromArray(pointsToTrack); //the MatofPoint '''features''' is now populated with the Shi-Tomasi points

            //set prevFeatures equal to features (since we only have one newly created MatofPoint), so equal to list of points created above
            prevFeatures.fromList(features.toList());
        }

        //set nextFeatures equal to prevFeatures. I think we have to do this as safety thing in case some of nextFeatures can't be populated by
        //the algorithm, since we iterate through all of nextFeatures after it runs to extract the deltaX and deltaY
        nextFeatures.fromArray(prevFeatures.toArray());

        /*run the Lucas-Kanade algo
        @param prevImg – first 8-bit input image or pyramid constructed by buildOpticalFlowPyramid()
        @param nextImg – second input image or pyramid of the same size and the same type as prevImg.
        @param prevPts – vector of 2D points for which the flow needs to be found; point coordinates must be single-precision floats.
            I think this is where we feed in Shi-Tomasi points
        @param nextPts – output vector of 2D points (w/single-precision float coords) containing calculated new
            positions of input features in the 2nd image; when OPTFLOW_USE_INITIAL_FLOW flag is passed, vector must have same size as input
        @param status – output status vector (of unsigned chars); each element of the vector is set to 1 if the flow for the corresponding
            features has been found, otherwise, it is set to 0.
        @param err – output vector of errors; each element of the vector is set to an error for the corresponding feature, type of the error
            measure can be set in flags parameter; if the flow wasn’t found then the error is not defined (use the status parameter to find
            such cases).
         */

        //here we pass the previous gray Mat as the first 8-bit image, the current gray Mat as second image, the newest Shi-Tomasi pts as prevFeatures,
        //and a MatofPoint nextFeatures which by default is the same as prevFeatures but should be modified
        Video.calcOpticalFlowPyrLK(mPrevGray, mGray, prevFeatures, nextFeatures, status, err); //features we track are the ones from goodFeaturesToTrack()

        //create two lists of points, one of the old goodFeatures, one of current goodFeatures traced/found by Lucas-Kanade algorithm
        List<Point> prevList = features.toList(), nextList = nextFeatures.toList();

        //define a color
        Scalar color = new Scalar(200, 0, 0);

        //get the number of goodFeatures there were initially
        int listSize = prevList.size();

        //iterate over all items in the Point Lists
        for (int i = 0; i < listSize; i++) {


            if (prevList.get(i) != null) {
                Log.i(TAG, String.format("This point in prevList is %f, %f", prevList.get(i).x, prevList.get(i).y));
                //tally up x and y values for previous frame
                xAvg1 += prevList.get(i).x;
                yAvg1 += prevList.get(i).y;
            }

            if (nextList.get(i) != null) {
                //tally up x and y values for this frame
                xAvg2 += nextList.get(i).x;
                yAvg2 += nextList.get(i).y;
            }

            //draw out a line on the mGrayT image Mat from the previous point of interest to the location it moved to in this frame
            Imgproc.line(mGray, prevList.get(i), nextList.get(i), color, 3);
        }

        //finish calculating the X and Y averages of all points of interest for both the previous frame and this frame
        xAvg1 /= listSize;
        xAvg2 /= listSize;
        yAvg1 /= listSize;
        yAvg2 /= listSize;

        //get the average shift in x and y in pixels between last frame and this frame
        double pointX = xAvg1 - xAvg2;
        double pointY = yAvg1 - yAvg2;

        //if our List of Points (the global one) is empty (this is our first run of sparseFlw), add the average shift values as the first point
        if (points.isEmpty()) {
            points.add(new Point(pointX, pointY));
        }

        //otherwise there are already some points in the list
        else {
            //get the most recent x and y shift values
            Point lastPoint = points.get(points.size() - 1);

            //add the cumulative running x and y shift totals to the ones for just this frame change
            pointX += lastPoint.x;
            pointY += lastPoint.y;

            //add the new cumulative deltaX and deltaY totals as point to end of the List
            points.add(new Point(pointX, pointY));
        }

        //hold onto this Mat because we'll use it as the previous frame to calc optical flow next time
        //capture the current mGrayT (Mat of all pixels from cam), clone it into mPrevGray for later use (clone() COPIES all pixels in memory)
        mPrevGray = mGray.clone();

        //return the final Mat
        return mGray;
    }
}
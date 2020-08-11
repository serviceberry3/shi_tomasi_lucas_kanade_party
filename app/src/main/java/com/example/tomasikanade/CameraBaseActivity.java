package com.example.tomasikanade;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;
import org.opencv.videoio.VideoCapture;

import java.security.Key;
import java.util.ArrayList;
import java.util.List;

public class CameraBaseActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, View.OnTouchListener {

    private static final String TAG = "CameraBaseActivity";

    //VIDEOCAPTURE DOESN'T WORK FOR ANDROID
    //private VideoCapture mCamera;

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

    Point[] goodFeaturesPrev = null, goodFeaturesNext = null;

    //initialize two matrices of points
    MatOfPoint2f prevFeatures, nextFeatures, thisFeatures, safeFeatures;
    MatOfPoint features;

    MatOfByte status;
    MatOfFloat err;
    List<Point> points;
    KeyFeature[] cornerList;

    //instance of MergeSort to serve as our sorter for everything (probably could use a Singleton?)
    MergeSort mergeSort;

    //stats
    Stats stats = new Stats();

    //the time it took to run both algorithms on the frame and get the data back; used to calculate a rough velocity of the device
    long lastInferenceTimeNanos;

    //the x and y displacement between the last two frames, along with the velocity calculated
    double pointX, pointY, xVel, yVel;

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

        cornerList = new KeyFeature[3];



        //TEST OUT THE STATS
        for (int i=0; i<3; i++) {
            cornerList[i] = new KeyFeature(null, 0, 0, i);
        }

        for (int i = 0; i < cornerList.length; i++) {
            Log.i(TAG, String.format("Stat testing Disp %f", cornerList[i].getDispVect()));
        }

        float[] testing = stats.IQR(cornerList, cornerList.length);
        Log.i(TAG, String.format("Stats found %f as IQR", testing[2]));

        //TEST OUT THE JENKS
        /*
        Jenks jenks = new Jenks();
        jenks.addValue(1.0);
        jenks.addValue(3.2);
        jenks.addValue(1.9);
        jenks.addValue(2.4);
        jenks.addValue(2.0);

        jenks.addValue(6.7);
        jenks.addValue(6.9);
        jenks.addValue(7.2);
        jenks.addValue(8.1);


        jenks.addValue(20.1);
        jenks.addValue(22.0);
        jenks.addValue(21.5);
        jenks.addValue(20.2);




        Jenks.Breaks breaks = jenks.computeBreaks(3);

        int[] results = breaks.breaks;

        for (int i = 0; i < 3; i++) {
            Log.i(TAG, String.format("Break at %d",results[i]));
        }

         */

        //setContentView(R.layout.camera_view);
    }

    //use this OpenCV loader callback to instantiate Mat objects, otherwise we'll get an error about Mat not being found
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            Log.i(TAG, "BaseLoaderCallback called!");
            if (status == LoaderCallbackInterface.SUCCESS) {//instantiate everything we need from OpenCV
                //mCamera = new VideoCapture();
                sceneColor = new Mat();
                sceneGrayScale = new Mat();

                //everything succeeded
                Log.i(TAG, "OpenCV loaded successfully, everything created");


                mOpenCvCameraView = new ShiTomasiView(CameraBaseActivity.this, 0);

                //set the camera listener callback to the one declared in this class
                mOpenCvCameraView.setCvCameraViewListener(CameraBaseActivity.this);


                mOpenCvCameraView.setOnTouchListener(CameraBaseActivity.this);

                //to improve speed and reduce lag of the algorithms, lower the camera frame quality
                mOpenCvCameraView.setMaxFrameSize(720, 1280); //720 x 1280?

                //make the camera view visible
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
        //cornerList = new ArrayList<>();
        nextFeatures = new MatOfPoint2f();
        thisFeatures = new MatOfPoint2f();
        safeFeatures = new MatOfPoint2f();
        status = new MatOfByte();
        err = new MatOfFloat();
        mergeSort = new MergeSort();
    }

    void rotate(Mat src, double angle, Mat dest) {
        int len = Math.max(src.cols(), src.rows());
        Point pt = new Point(len/2., len/2.);
        Mat r = Imgproc.getRotationMatrix2D(pt, angle, 1.0);
        Imgproc.warpAffine(src, dest, r, new Size(len, len));
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        //start the clock when this frame comes in. we'll get a split at the next frame and use elapsed time for velocity calculation
        long frameStartTime = SystemClock.elapsedRealtimeNanos();

        sceneColor = inputFrame.rgba();

        //convert the color image matrix to a grayscale one to improve memory usage and processing time (1 bpp instead of 3)
        //also converting to grayscale makes the contrast between features clearer
        Imgproc.cvtColor(sceneColor, sceneGrayScale, Imgproc.COLOR_BGRA2GRAY);

        //if the camera is just being loaded, get the Shi-Tomasi good features to track first time
        if (prevFeatures.rows() == 0) {
            Log.i(TAG, "prevFeatures is empty, now populating with Shi-Tomasi");

            //copy this image matrix to previous matrix
            sceneGrayScale.copyTo(mPrevGray);

            //get the current corners and put them in prevFeatures
            prevFeatures.fromArray(getCorners(sceneGrayScale));

            //get safe copy of these corners
            prevFeatures.copyTo(safeFeatures);

            //safeFeatures now holds the very first set of corners seen by the camera. Notice thisFeatures is still unpopulated, it wont' be populated
            //for first time until Lucas-Kanade is run for the first time on the below call to sparseFlow()
        }

        //otherwise this isn't the first frame, so we already have some features that we're tracking and some existent image mats
        else {
            //copy this image mat to previous one (mPrevGray)
            //sceneGrayScale.copyTo(mPrevGray);

            //get the corners for this current mat and store them in thisFeatures
            thisFeatures.fromArray(getCorners(sceneGrayScale));

            //retrieve the corners from the previous mat (save calculating them again)
            safeFeatures.copyTo(prevFeatures);

            //save these current corners for next time
            thisFeatures.copyTo(safeFeatures);
        }

        //run the sparse optical flow calculation on this grayscale frame and return the final Mat with the lines drawn
        Mat result = sparseFlow(sceneGrayScale);

        //get the time it took do do all calculations
        lastInferenceTimeNanos = SystemClock.elapsedRealtimeNanos() - frameStartTime;

        //Log.i(TAG, String.format("Time frame took: %d ns", lastInferenceTimeNanos));

        float timeInSec = lastInferenceTimeNanos * 1f / 1000000000;

        //Log.i(TAG, String.format("Time frame took: %f s", timeInSec));

        xVel = pointX / timeInSec;
        yVel = pointY / timeInSec;

        //create and rotate the text to display velocity
        Mat textImg = Mat.zeros(result.rows(), result.cols(), result.type());

        /*Imgproc.putText(result, String.format("Velocity(m/s) y: %f, x: %f", xVel, yVel), new Point(100, 100), Core.FONT_HERSHEY_SIMPLEX,
                0.5,
                new Scalar(255, 255, 255),
                0);

         */

        //rotate the text so it's facing the right way
        //rotate(textImg, -45, textImg);

        //Imgproc.cvtColor(textImg, textImg, Imgproc.COLOR_RGB2BGRA);

        //return textImg;

        //result = result + textImg;

        /*Log.i(TAG, String.format("The result mat has %d channels, textImg has %d channels", result.channels(), textImg.channels()));
        Log.i(TAG, String.format("The result mat has %d columns %d rows, textImg has %d col %d row",
                result.cols(),
                result.rows(),
                textImg.cols(),
                textImg.rows()));

         */

        //Imgproc.cvtColor(textImg, textImg, Imgproc.COLOR_BGRA2GRAY);

       //Core.add(result, textImg, result);

        return result;
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
        //we store the returned corners in '''corners'''
        Imgproc.goodFeaturesToTrack(sceneGrayScale, corners, maxCorners, qualityLevel, minDistance, new Mat(), blockSize, useHarrisDetector, k);

        //get array of points from corners (filled in by the algorithm)
        Point[] pointsFound = corners.toArray();

        //Log.i(TAG, String.format("Found %d points to draw", points.length));

        /*
        //draw all of the key points on the screen
        for (Point p : points) {
            //what is core?
            Imgproc.circle(sceneGrayScale, p, 2, circleColor, 10);
        }
         */

        //return the array of Points that holds the corners
        return pointsFound;
    }


    //This is a Lucas-Kanade processor for a given Mat
    public Mat sparseFlow(Mat inputFrame) {
        //get the CURRENT grayscale Mat from the input camera frame
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

        //initialize some doubles to hold the average position of the corners in the previous frame vs the current frame
        //average position of the goodFeatures in previous frame
        double xAvg1 = 0;
        double yAvg1 = 0;

        //average position of the goodFeatures in this frame
        double xAvg2 = 0;
        double yAvg2 = 0;

        //if features is empty, that means we don't have any points to work with yet
        /*
        if (features.toArray().length == 0) {
            Log.i(TAG, "features is empty, now populating with Shi-Tomasi points");

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



            //the MatofPoint class in OpenCV is a 2D array of points. We can add all of our Shi-Tomasi points into our MatofPoint instance
            //by calling fromArray() on the array of Points
            features.fromArray(pointsToTrack); //the MatofPoint '''features''' is now populated with the Shi-Tomasi points

            //set prevFeatures equal to features (since we only have one newly created MatofPoint), so equal to list of points created above
            prevFeatures.fromList(features.toList());
        }


         */

        //set nextFeatures equal to prevFeatures. I think we have to do this as safety thing in case some of nextFeatures can't be populated by
        //the algorithm, since we iterate through all of nextFeatures after it runs to extract the deltaX and deltaY
        //nextFeatures.fromArray(prevFeatures.toArray());


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

        //here we pass the previous gray Mat as the first 8-bit image, the current gray Mat as second image, last frame's Shi-Tomasi pts as prevFeatures,
        //and a MatofPoint nextFeatures which by default is the same as prevFeatures but should be modified
        //NOTE: on first call of sparseFlow(), mPrevGray will = mGray, prevFeatures will hold goodFeatures of current frame, thisFeatures will be empty
        Video.calcOpticalFlowPyrLK(mPrevGray, mGray, prevFeatures, thisFeatures, status, err); //features we track are the ones from goodFeaturesToTrack()

        //create two lists of points, one of the goodFeatures from previous frame, one of current goodFeatures traced/found by Lucas-Kanade algorithm
        List<Point> prevList = prevFeatures.toList(), nextList = thisFeatures.toList();

        //get the statuses (statii?) after the algorithm run
        List<Byte> byteStatus = status.toList();

        int y = byteStatus.size() - 1;

        //define a color for our tracking lines
        Scalar color = new Scalar(255, 0, 0);

        //know when to log
        boolean test = false;

        //get the number of goodFeatures there were initially
        int listSize = prevList.size();

        //create array of KeyFeatures the size of byteStatus list
        cornerList = new KeyFeature[y];

        //iterate over all items in the Point Lists
        for (int i = 0; i < y /*listSize*/; i++) {
            //get the previous point and current point corresponding to this feature
            Point prevPt = prevList.get(i);
            Point nextPt = nextList.get(i);

            //calculate the x and y displacement for this pt. Remember the origin is top right, not bottom left
            double xDiff = prevPt.x - nextPt.x;
            double yDiff = prevPt.y - nextPt.y;

            //pythagorean to get the absolute displacement magnitude
            double dispVect = Math.sqrt(Math.pow(xDiff,2) + Math.pow(yDiff,2));

            //now make a new KeyFeature and store the CURRENT coordinates (Point), x displacement, y displacement, and "overall" displacement magnitude
            KeyFeature thisFeature = new KeyFeature(nextPt, xDiff, yDiff, dispVect);

            //push the feature to the ArrayList of KeyFeatures
            cornerList[i] = thisFeature;

            if (prevPt != null) {
                //Log.i(TAG, String.format("This point in prevList is %f, %f", prevList.get(i).x, prevList.get(i).y));
                //tally up x and y values for previous frame
                xAvg1 += prevPt.x;
                yAvg1 += prevPt.y;
            }

            if (nextPt != null) {
                //tally up x and y values for this frame
                xAvg2 += nextPt.x;
                yAvg2 += nextPt.y;

                if (test==false) {
                    //Log.i(TAG, String.format("Point originally at (%f, %f), moved to (%f, %f)", prevList.get(i).x, prevList.get(i).y, nextList.get(i).x, nextList.get(i).y));
                    test = true;
                }
            }

            //modify the nextPt a bit to exaggerate it so that we can see the motion lines better (can remove this if desire)
            Point prevPtExagg = new Point(prevPt.x +  xDiff , prevPt.y + yDiff); //double length of line

            //if (prevPt!=null && nextPt!=null) {
            if (byteStatus.get(i)==1 && nextPt!=null && prevPt!=null) {
                //draw out a line on the current grayScale image Mat from the previous point of interest to the location it moved to in this frame
                Imgproc.line(mGray, nextPt, prevPtExagg, color, 3);
            }

            //Log.i(TAG, String.format("Line from (%f, %f) to (%f, %f)", prevList.get(i).x, prevList.get(i).y, nextList.get(i).x, nextList.get(i).y));

            //Imgproc.line(mGray, new Point(10,200), new Point(300,200), color, 3);
        }

        //sort all of the points found by the amount they've moved since the last frame
        mergeSort.sort(cornerList, 0, cornerList.length - 1);

        //print out the list of displacements(checking to see if sort worked)
        for (int i = 0; i < y; i++) {
            //Log.i(TAG, String.format("Disp %f", cornerList[i].getDispVect()));
        }

        /*
        //run stats on the points and get the interquartile range
        float[] quartileStats = stats.IQR(cornerList, cornerList.length);

        if (quartileStats != null) {
            float outlierCutoff = 11f * quartileStats[2];

            float outlierLow = (float) (quartileStats[0] - outlierCutoff);
            float outlierHigh = (float) (quartileStats[1] + outlierCutoff);

            for (int i = 0; i < y; i++) {
                float thisDisp = (float) cornerList[i].getDispVect();
                if (thisDisp >= outlierHigh) {
                    Log.i(TAG, String.format("Found high outlier with disp %f", thisDisp));
                }
            }
        }
         */



        //finish calculating the X and Y averages of all points of interest for both the previous frame and this frame
        xAvg1 /= listSize;
        xAvg2 /= listSize;
        yAvg1 /= listSize;
        yAvg2 /= listSize;

        //get the average shift in x and y in pixels between last frame and this frame
        //DON'T FORGET the origin is top right, not bottom left
        pointX = xAvg1 - xAvg2; //x displacement
        pointY = yAvg1 - yAvg2; //y displacement

        //we need some way to get the time between frames

        //if our List of Points (the global one) is not empty (this is our first run of sparseFlw), calculate the cumulative total shift in x and y
        if (!points.isEmpty()) {
            //get the most recent x and y shift values
            Point lastPoint = points.get(points.size() - 1);

            //add the cumulative running x and y shift totals to the ones for just this frame change
            pointX += lastPoint.x;
            pointY += lastPoint.y;
        }

        //if list empty, just add deltaX and deltaY totals as first pt in the List. If it wasn't empty add the new cumulative totals to end of list
        points.add(new Point(pointX, pointY));

        //hold onto this Mat because we'll use it as the previous frame to calc optical flow next time
        //capture the current mGrayT (Mat of all pixels from cam), clone it into mPrevGray for later use (clone() COPIES all pixels in memory)
        mPrevGray = mGray.clone();

        //thisFeatures.copyTo(prevFeatures);

        //return the final Mat
        return mGray;
    }
}
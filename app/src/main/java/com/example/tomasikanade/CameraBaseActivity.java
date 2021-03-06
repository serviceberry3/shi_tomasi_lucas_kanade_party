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

import java.lang.reflect.Array;
import java.security.Key;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CameraBaseActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, View.OnTouchListener {

    private static final String TAG = "CameraBaseActivity";

    //VIDEOCAPTURE DOESN'T WORK FOR ANDROID
    //private VideoCapture mCamera;

    //Mat class represents an n-dimensional dense numerical single-channel or multi-channel array. Can be used to store real or
    //complex-valued vectors and matrices, grayscale or color images, voxel volumes, vector fields, point clouds, tensors, histograms

    //use 2 Mats to store the camera image, one in color (RGB), and one black and white
    private Mat sceneGrayScale, sceneColor, mGray, mPrevGray, result, image;


    private CameraBridgeViewBase mOpenCvCameraView;

    private int frameCounter = 0, discards, numPts;

    private double[]means;

    //break the key features into two groups based on displacement
    private final int numGroups = 2;

    //Values needed for the corner detection algorithm Most likely have to tweak them to suit needs. Could also
    //let the application find out the best values by itself.
    private final static double qualityLevel = 0.25; //.35
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


    KeyFeature[] cornerList, cornerListSorted;

    List<Point> prevList, nextList, cornersFoundGoingBackList, forwardBackErrorList;
    List<Byte> byteStatus;
    ArrayList<Point> nextListCorrected;

    //define a color for drawing
    Scalar color = new Scalar(255, 0, 0);

    //instance of MergeSort to serve as our sorter for everything (probably could use a Singleton?)
    MergeSort mergeSort;

    //instance of Jenks to serve as our Jenks Natural Breaks machine for everything
    Jenks jenks;

    //stats
    Stats stats = new Stats();

    //the time it took to run both algorithms on the frame and get the data back; used to calculate a rough velocity of the device
    long lastInferenceTimeNanos;

    //the x and y displacement between the last two frames, along with the velocity calculated
    double pointX, pointY, xVel, yVel;

    //initialize some doubles to hold the average position of the corners in the previous frame vs the current frame

    //average position of the goodFeatures in previous frame
    double xAvg1 = 0, yAvg1 = 0,
            //average position of the goodFeatures in this frame
            xAvg2 = 0, yAvg2 = 0;

    private int[] breakPoints;


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

        cornerList = new KeyFeature[10];

        //TEST OUT THE STATS
        /*
        for (int i = 0; i < 3; i++) {
            cornerList[i] = new KeyFeature(null, 0, 0, i);
        }*/

        cornerList[0] = new KeyFeature(null, 0, 0, 0.45);
        cornerList[1] = new KeyFeature(null, 0, 0, 1.60);
        cornerList[2] = new KeyFeature(null, 0, 0, 2.54);
        cornerList[3] = new KeyFeature(null, 0, 0, 3.98);
        cornerList[4] = new KeyFeature(null, 0, 0, 4.12);
        cornerList[5] = new KeyFeature(null, 0, 0, 5.09);
        cornerList[6] = new KeyFeature(null, 0, 0, 5.45);
        cornerList[7] = new KeyFeature(null, 0, 0, 6.97);
        cornerList[8] = new KeyFeature(null, 0, 0, 6.99);
        cornerList[9] = new KeyFeature(null, 0, 0, 7.23);


        for (int i = 0; i < cornerList.length; i++) {
            Log.i(TAG, String.format("Stat testing Disp %f", cornerList[i].getDispVect()));
        }

        float[] testing = stats.IQR(cornerList, cornerList.length, 0);
        Log.i(TAG, String.format("Stats found %f as IQR for entire 10-num array", testing[2]));

        float[] testing2 = stats.IQR(cornerList, 4 + 1, 0);
        Log.i(TAG, String.format("Stats found %f as IQR for 0-4 of array", testing2[2]));

        float[] testing3 = stats.IQR(cornerList, cornerList.length, 5);
        Log.i(TAG, String.format("Stats found %f as IQR for 5-9 of array", testing3[2]));



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

        /*
        //RUN FINISHED JENKS TEST
        KeyFeature feat1 = new KeyFeature(null, 0, 0, 2),
                feat2 = new KeyFeature(null, 0, 0, 1),
                feat3 = new KeyFeature(null, 0, 0, 1.5),
                feat4 = new KeyFeature(null, 0, 0, 8),
                feat5 = new KeyFeature(null, 0, 0, 9.2),
                feat6 = new KeyFeature(null, 0, 0, 8.4);

        KeyFeature[] testFeatureList = new KeyFeature[6];
        testFeatureList[0] = feat1;
        testFeatureList[1] = feat2;
        testFeatureList[2] = feat3;
        testFeatureList[3] = feat4;
        testFeatureList[4] = feat5;
        testFeatureList[5] = feat6;

        Jenks jenks = new Jenks(testFeatureList);
        Jenks.Breaks breaks = jenks.computeBreaks(2);

        int[] results = breaks.breaks;
        for (int i = 0; i < 2; i++) {
            Log.i(TAG, String.format("Break at %d", results[i]));
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
        discards = 0;
    }

    void rotate(Mat src, double angle, Mat dest) {
        int len = Math.max(src.cols(), src.rows());
        Point pt = new Point(len/2., len/2.);
        Mat r = Imgproc.getRotationMatrix2D(pt, angle, 1.0);
        Imgproc.warpAffine(src, dest, r, new Size(len, len));
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        discards = 0;

        //start the clock when this frame comes in. we'll get a split at the next frame and use elapsed time for velocity calculation
        long frameStartTime = SystemClock.elapsedRealtimeNanos();

        sceneColor = inputFrame.rgba();

        //convert the color image matrix to a grayscale one to improve memory usage and processing time (1 bpp instead of 3)
        //also converting to grayscale makes the contrast between features clearer
        Imgproc.cvtColor(sceneColor, sceneGrayScale, Imgproc.COLOR_BGRA2GRAY);

        /*
        //only do computations every 5 frames
        if (frameCounter != 4) {
            frameCounter++;
            return result;
        }
        else {
            frameCounter = 0;
        }
         */

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

            result = sparseFlow(sceneGrayScale);
        }

        //otherwise this isn't the first frame, so we already have some features that we're tracking and some existent image mats
        else {
            //copy this image mat to previous one (mPrevGray)
            //sceneGrayScale.copyTo(mPrevGray);

            //retrieve the corners from the previous mat (save calculating them again)
            safeFeatures.copyTo(prevFeatures);

            //To speed up processing, we only want to run Shi-Tomasi corner finder every 5 frames or so, so check if this is a Shi-Tomasi frame
            if (frameCounter == 5) {
                Log.i(TAG, "Recalculating corners");

                frameCounter = 0;

                //get the corners for this current mat and store them in thisFeatures
                thisFeatures.fromArray(getCorners(sceneGrayScale));
            }
            //else we want thisFeatures to be populated with the Lucas-Kanade points found from tracking prevFeatures into this frame
            else {
                result = sparseFlow(sceneGrayScale);
                //now thisFeatures will be populated with LK pts
            }

            //save these current LK corners for next time
            thisFeatures.copyTo(safeFeatures);
        }

        //run the sparse optical flow calculation on this grayscale frame and return the final Mat with the lines drawn
        //result = sparseFlow(sceneGrayScale);

        if (result == null) {
            Log.e(TAG, "sparseFlow returned NULL");
            return null;
        }

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

        frameCounter++;
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
    @RequiresApi(api = Build.VERSION_CODES.N)
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

        //run the LK algorithm forwards and backwards
        lucasKanadeForwardBackward();

        //throw out points that don't match between last frame and this frame
        throwOutWanderingPts();

        //get the displacements of the tracked points and fill in cornerList[] appropriately (cornerList is final list of all KeyFeatures being considered for drawing)
        populateDisplacements();

        //get the number of goodFeatures there were initially
        int listSize = prevList.size();

        //make copy of cornerList and remove the KeyFeatures with NULL pts (that failed forward-backward test). This copy will be used for sorting, Jenks, etc. Then

        //copy cornerList, remove invalid KeyFeatures from the copy, and sort
        getSortedCornerList();

        //separate the points into Jenks Natural Breaks groups
        doJenksSeparation();

        if (breakPoints == null) {
            return null;
        }

        means = new double[numGroups];
        //Log.i(TAG, String.format("Length of means: %d", means.length));

        int currStart = 0;

        if (breakPoints.length > 1) {
            //once we get the breakpoint, I want to compare the means of the two clusters found to see if they really differ much. If they
            //don't differ much, the grouping can be ignored
            for (int i = 0; i < numGroups; i++) {
                //if this is the first iteration, start index is 0, end index is first int from breakPoint array

                //get the mean for this grouping and store it in means array
                means[i] = Jenks.Breaks.mean(cornerListSorted, currStart, breakPoints[i]);
            }
        }

        //print out the means of the two groups found
        for (int i = 0; i < numGroups; i++) {
            Log.i(TAG, String.format("Mean of group %d is %f", i, means[i]));
        }

        //treating the different motion groups separately, remove outliers from the groups
        removeOutliersWithinClusters();

        //draw the displacement lines on the image
        drawDisplacementLines();


        //if there's an area in the image that's moving faster than the rest, let's draw a rectangle around ti
        drawMotionRectangle();


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

    /**
     * Run the Lucas-Kanade algorithm on the previous frame and this frame, both tracking last frame's Shi-Tomasi pts forward to this frame and tracking
     * those found LK pts backward to the last frame to minimize error due to object obstruction, noise, etc.
     */
    public void lucasKanadeForwardBackward() {
        /**
         * Run the Lucas-Kanade algo with the following passed for the parameters:
         *
         * We pass the previous gray Mat as the first 8-bit image, the current gray Mat as second image, last frame's Shi-Tomasi pts as prevFeatures,
         * and a MatofPoint nextFeatures which by default is the same as prevFeatures but should be modified
         *
         * NOTE: on first call of sparseFlow(), mPrevGray will = mGray, prevFeatures will hold goodFeatures of current frame, thisFeatures will be empty
         *
         * @param prevImg – first 8-bit input image or pyramid constructed by buildOpticalFlowPyramid()
         * @param nextImg - second input image which represents the current frame
         * @param prevPts – vector of 2D points for which the flow needs to be found; point coordinates must be single-precision floats.
         *       I think this is where we feed in Shi-Tomasi points
         * @param nextPts – output vector of 2D points (w/single-precision float coords) containing calculated new
         *       positions of input features in the 2nd image; when OPTFLOW_USE_INITIAL_FLOW flag is passed, vector must have same size as input
         * @param status – output status vector (of unsigned chars); each element of the vector is set to 1 if the flow for the corresponding
         *       features has been found, otherwise, it is set to 0.
         * @param err – output vector of errors; each element of the vector is set to an error for the corresponding feature, type of the error
         *             measure can be set in flags parameter; if the flow wasn’t found then the error is not defined (use the status parameter to find
         *             such cases).
         */
        Video.calcOpticalFlowPyrLK(mPrevGray, mGray, prevFeatures, thisFeatures, status, err); //Features we track are the ones from goodFeaturesToTrack()

        //Now thisFeatures will contain the corners from last frame that were supposedly tracked into this frame

        //Create new matrix of (x,y) float coords to store the result of running LK algorithm backwards
        MatOfPoint2f cornersFoundGoingBackwards = new MatOfPoint2f();

        //To reduce error and noise, we'll also run the algorithm backwards, treating the points found by LK in second frame as the first/original set of pts
        //and the first frame as the next frame. Then we'll compare the backtracked LK-generated points in the first frame with those originally generated in the
        //first frame by Shi-Tomasi. If there's a discrepancy for one of the points, that means that point probably was obstructed, etc. in the second frame,
        //which caused LK to find a different point for it. Thus the backtracked LK point found will differ greatly from the original S-T point. In this case, the
        //forward trajectory and displacement from last frame to this frame is invalid for that point and should be thrown out.
        Video.calcOpticalFlowPyrLK(mGray, mPrevGray, thisFeatures, cornersFoundGoingBackwards, status, err);

        //Create new matrix of (x, y) float coords to store difference between points found
        MatOfPoint2f difference = new MatOfPoint2f();

        Log.i(TAG, String.format("Prevfeatures has %d columns, %d rows. Cornersfoundback has %d col, %d row", prevFeatures.cols(),
                prevFeatures.rows(),
                cornersFoundGoingBackwards.cols(),
                cornersFoundGoingBackwards.rows()));

        //Subtract [the Mat containing features found in last frame by directly running Shi-Tomasi] from [Mat containing features found in last frame
        //by running LK backwards on LK-tracked points found in this frame] to get the forward-backward error. This error will help us determine whether we should
        //trust the supposed points tracked by LK into this frame, because if a pt was found in this frame but not backtracked to the previous frame then it should
        //probably be thrown out
        Core.subtract(prevFeatures, cornersFoundGoingBackwards, difference);

        Log.i(TAG, String.format("Difference initially has %d columns, %d rows.", difference.cols(), difference.rows()));

        //Convert the difference matrix into a matrix with just two rows for easy iteration
        //difference.reshape(-1, 2);

        Log.i(TAG, String.format("Difference has %d columns, %d rows.", difference.cols(), difference.rows()));

        Log.i(TAG, String.format("Value from difference is %f, %f", difference.toList().get(0).x, difference.toList().get(0).y));

        //Create (x,y) List of pts for the goodFeatures from previous frame
        prevList = prevFeatures.toList();

        //Create (x,y) List of pts for the current goodFeatures traced/found by FORWARD Lucas-Kanade algorithm
        nextList = thisFeatures.toList();

        //Create (x,y) List of pts for the goodFeatures found by BACKWARD Lucas-Kanade algorithm
        cornersFoundGoingBackList = cornersFoundGoingBackwards.toList();

        //Create (x,y) List of pts for difference between the actual Shi-Tomasi corner pts in the last frame and the ones supposedly found by LK in this frame
        forwardBackErrorList = difference.toList();

        //Now everything has been prepared for further processing; namely, the points have been prepared for forward-backward error removal/filtering.
    }

    /**
     * Remove "wandering," obstructed, and/or hard-to-track points from the next features list
     */
    public void throwOutWanderingPts() {
        //get the statuses (statii?) after the algorithm run
        byteStatus = status.toList();

        //get size of the status list, which equals the size of the forwardBackErrorList and the number of points we're dealing with
        numPts = byteStatus.size() - 1;

        //create array of KeyFeatures the size of byteStatus list to server as our corrected nextList
        cornerList = new KeyFeature[numPts];

        //instantiate a new ArrayList for nextListCorrected to prepare for adding valid points
        //nextListCorrected = new ArrayList<>();

        //REMOVE CERTAIN PTS BASED ON FORWARD-BACKWARD ERROR

        //Iterate through the list of differences, find max of x and y difference; if it's >= a certain val, then don't add that pt to nextList
        for (int i = 0; i < numPts; i++) {
            double xErr = forwardBackErrorList.get(i).x, yErr = forwardBackErrorList.get(i).y;

            double maxError = Math.max(xErr, yErr);
            Log.i(TAG, String.format("Max error found to be %f", maxError));

            //If there was a lot of error between forward run of LK and backward run, throw this point out; don't add it to the next features list
            if (maxError >= 0.01) {
                Log.i(TAG, "Throwing out point");

                //set this point in the nextList to null to invalidate it
                nextList.set(i, null);
                discards++;
            }
        }
    }

    public void populateDisplacements() {
        //iterate over all items in the Point Lists and get and store the displacement
        for (int i = 0; i < numPts /*listSize*/; i++) {
            //get the previous point and current point corresponding to this feature
            Point prevPt = prevList.get(i);
            Point nextPt = nextList.get(i);  //***CHECKKKK

            //only fill in the displacement fields if this point was found to be valid in the forward-backward check
            if (nextPt != null && prevPt != null) {
                //calculate the x and y displacement for this pt. Remember the origin is top right, not bottom left
                double xDiff = prevPt.x - nextPt.x;
                double yDiff = prevPt.y - nextPt.y;

                //pythagorean to get the absolute displacement magnitude
                double dispVect = Math.sqrt(Math.pow(xDiff, 2) + Math.pow(yDiff, 2));

                //now make a new KeyFeature and store the CURRENT coordinates (Point), x displacement, y displacement, and "overall" displacement magnitude
                KeyFeature thisFeature = new KeyFeature(nextPt, xDiff, yDiff, dispVect);

                //push the feature to the ArrayList of KeyFeatures
                cornerList[i] = thisFeature;

                //tally up x and y values for the last frame
                xAvg1 += prevPt.x;
                yAvg1 += prevPt.y;

                //tally up x and y values for this frame
                xAvg2 += nextPt.x;
                yAvg2 += nextPt.y;
            }
        }
    }

    public void doJenksSeparation() {
        /* RESERVED FOR TESTING
        KeyFeature[] testing = new KeyFeature[1];
        testing[0] = new KeyFeature(new Point(2,2), 0,0,3);
         */

        //Use Jenks API to sort the the displacement data into (for now, 2) groups
        jenks = new Jenks(cornerListSorted);

        //compute the breakpoints/run the algo
        Jenks.Breaks breaks = jenks.computeBreaks(numGroups);

        //check to make sure computing breaks was successful
        if (breaks == null) {
            Log.e(TAG, "Jenks breaks computation failed, returning now");
            this.breakPoints = null;
            return;
        }

        int[] breakPoints = breaks.breaks;

        if (breakPoints.length != 0) {
            //Log.i(TAG, String.format("%d features in cornerList, found breakpoint at %d", cornerList.length, breakPoints[0]));
        }

        this.breakPoints = breakPoints;
    }

    public void removeOutliersWithinClusters() {
        //GET READY TO DRAW THE DISPLACEMENT LINES - we'll treat the two groups (high and low) separately and remove any outliers within them first
        int start = 0;
        for (int i = 0; i < numGroups; i++) {
            //make sure this breakpoint exists
            if ((breakPoints.length >= i + 1)) {
                int n;
                Log.i(TAG, String.format("Removing outliers for group #%d", i));

                //run stats on the points and get the interquartile range
                Log.i(TAG, String.format("We have a cornerList of len %d, calling IQR(list, n: %d, start: %d)", cornerList.length, breakPoints[i] + 1, start));

                float[] quartileStats = stats.IQR(cornerListSorted, breakPoints[i] + 1, start);

                if (quartileStats != null) {
                    //Multiply the IQR by a certain number (standard is 1.5) to get the outlier cutoff deviation from Q1 and Q3
                    float outlierCutoff = 1.5f * quartileStats[2];

                    //find the non-outlier range (we really only care about the high cutoff, though
                    float outlierLow = (float) (quartileStats[0] - outlierCutoff);
                    float outlierHigh = (float) (quartileStats[1] + outlierCutoff);

                    for (int j = start; j <= breakPoints[i]; j++) {
                        float thisDisp = (float) cornerListSorted[j].getDispVect();


                        if (thisDisp >= outlierHigh) {
                            Log.i(TAG, String.format("Found high outlier in group %d with disp %f", i, thisDisp));
                        }
                    }

                    //kick out the outliers by invalidating them (setting valid field to 0)
                    stats.rmOutliers(cornerListSorted, outlierHigh, 0, cornerListSorted.length - 1);

                    Log.i(TAG, String.format("Now setting start to %d", breakPoints[i] + 1));
                    start = breakPoints[i] + 1;
                }
            }
        }
    }

    public void drawDisplacementLines() {
        //iterate over all the points in cornerList and, if they're valid, draw the displacement lines
        for (int i = 0; i < numPts /*listSize*/; i++) {
            //get the previous point and current point corresponding to this feature
            Point prevPt = prevList.get(i);
            Point nextPt = nextList.get(i);

            if (nextPt!=null && prevPt!=null) {
                //calculate the x and y displacement for this pt. Remember the origin is top right, not bottom left
                double xDiff = prevPt.x - nextPt.x;
                double yDiff = prevPt.y - nextPt.y;

                //get the KeyFeature corresponding to nextPt from the cornerList so that we can make sure it's valid
                KeyFeature thisFeature = cornerList[i];

                //a valid field of 0 means this point was an outlier. A getPt result of null means this point failed the forward-backward test
                if (thisFeature.isValid() && thisFeature.getPt() != null) {
                    //modify this current pt a bit to exaggerate it so that we can see the motion lines better (can remove this if desire)
                    Point prevPtExagg = new Point(prevPt.x + xDiff, prevPt.y + yDiff); //double length of line


                    //if (prevPt!=null && nextPt!=null) {
                    if (byteStatus.get(i) == 1) {
                        //draw out a line on the current grayScale image Mat from the previous point of interest to the location it moved to in this frame
                        Imgproc.line(mGray, nextPt, prevPtExagg, color, 3);
                    }

                }
            }

            //Log.i(TAG, String.format("Line from (%f, %f) to (%f, %f)", prevList.get(i).x, prevList.get(i).y, nextList.get(i).x, nextList.get(i).y));

            //Imgproc.line(mGray, new Point(10,200), new Point(300,200), color, 3);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N) //require nougat
    public void getSortedCornerList() {
        //make a copy of the original cornerList
        cornerListSorted = new KeyFeature[cornerList.length - discards];

        Log.i(TAG, "Corner list pre-sorting");
        for (KeyFeature keyFeature : cornerList) {
            if (keyFeature == null) {
                Log.i(TAG, "This slot of cornerList NULL");
            } else {
                Log.i(TAG, String.format("This slot of cornerList: %f, %f", keyFeature.getPt().x, keyFeature.getPt().y));
            }
        }

        cornerListSorted = Arrays.stream(cornerList).filter(x -> !(x==null)).toArray(KeyFeature[]::new);

        //sort all of the points found by the amount they've moved since the last frame
        mergeSort.sort(cornerListSorted, 0, cornerList.length - discards - 1);

        Log.i(TAG, "Corner list post-sorting and removing nulls");
        for (int i=0; i<cornerList.length - discards; i++) {
            if (cornerListSorted[i] == null) {
                Log.i(TAG, "This slot of cornerListSorted NULL");
            }
            else {
                Log.i(TAG, String.format("This slot of cornerList: %f, %f", cornerListSorted[i].getPt().x, cornerListSorted[i].getPt().y));
            }
        }
    }

    public void drawMotionRectangle() {
        int fastGroup, slowGroup;
        double fastMean, slowMean;

        //Find which group is the fast and which is slow (for now I'm only doing two groups)
        if (means[0] >= means[1]) {
            fastMean = means[0];
            fastGroup = 0;
            slowGroup = 1;
            slowMean = means[1];
        }
        else {
            fastMean = means[1];
            fastGroup = 1;
            slowGroup = 0;
            slowMean = means[0];
        }

        //Check to see if the two groups of points differ greatly in their avg displacement since last frame
        if (fastMean / slowMean >= 1.85) { //2.65
            Log.i(TAG, String.format("DETECTED for %f", means[0]));
            //If it seems like there's a set of points moving faster relative to everything else, draw a box on the screen that approximates
            //those points (ALGORITHM SUBJECT TO MODIFICATION)

            //First, of the grouping of pts that seems to be moving faster, we want to find the most top-left and most top-right points for the rectangle

            //max/min out the rectangle bound to start
            double left = Double.MIN_VALUE, top = Double.MAX_VALUE, right = Double.MAX_VALUE, bottom = Double.MIN_VALUE;

            //Loop through the points
            for (int i = ((fastGroup == 0) ? 0 : breakPoints[0] + 1); i <= breakPoints[fastGroup]; i++) {
                //retrieve the x and y coordinates of this OpenCV Point (will be doubles)
                double thisX = cornerListSorted[i].getPt().x, thisY = cornerListSorted[i].getPt().y;

                if (thisX < right)
                    right = thisX;
                if (thisX > left)
                    left = thisX;
                if (thisY > bottom)
                    bottom = thisY;
                if (thisY < top)
                    top = thisY;
            }

            if (left == Double.MIN_VALUE)
                left = right;
            if (right == Double.MAX_VALUE)
                right = left;
            if (top == Double.MAX_VALUE)
                top = bottom;
            if (bottom == Double.MIN_VALUE)
                bottom = top;

            Imgproc.rectangle (
                    mGray,                          //the image we're looking to draw on
                    new Point(left, top),        //p1 (top left of rectangle when phone rotated left to landscape)
                    new Point(right, bottom),       //p2 (bottom right of rect when phone rotated left to landscape)
                    new Scalar(0, 0, 255),          //Scalar object for color
                    5                      //Thickness of the line for the rectangle
            );

        }
        //if they don't differ greatly, don't draw the rectangle
    }
}
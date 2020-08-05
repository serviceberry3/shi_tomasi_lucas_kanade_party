package com.example.tomasikanade;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.osgi.OpenCVInterface;
import org.opencv.osgi.OpenCVNativeLoader;
import org.opencv.videoio.VideoCapture;

import javax.security.auth.login.LoginException;

public class ShiTomasiView extends CvViewBase {
    //Mat class represents an n-dimensional dense numerical single-channel or multi-channel array. Can be used to store real or
    //complex-valued vectors and matrices, grayscale or color images, voxel volumes, vector fields, point clouds, tensors, histograms

    //use 2 Mats to store the camera image, one in color (RGB), and one black and white
    private Mat sceneColor;
    private Mat sceneGrayScale;

    //Values needed for the corner detection algorithm Most likely have to tweak them to suit needs. Could also
    //let the application find out the best values by itself.
    private final static double qualityLevel = 0.35;
    private final static double minDistance = 10;
    private final static int blockSize = 8;
    private final static boolean useHarrisDetector = false;
    private final double k = 0.0;
    private final static int maxCorners = 100;
    private final static Scalar circleColor = new Scalar(0, 255, 0);
    private VideoCapture mCamera;


    public ShiTomasiView(Context context, VideoCapture camera, Mat color, Mat grayscale) {
        super(context, camera, color, grayscale);
        this.mCamera = camera;
        this.sceneColor = color;
        this.sceneGrayScale = grayscale;
    }

    @Override
    protected Bitmap processFrame(VideoCapture capture) {
        //retrieve frame from the camera and put it in the color matrix
        capture.retrieve(sceneColor, 2); //2 = CV_CAP_ANDROID_COLOR_FRAME_RGB

        //convert the color image matrix to a grayscale one to improve memory usage and processing time (1 bpp instead of 3)
        //also converting to grayscale makes the contrast between features clearer
        Imgproc.cvtColor(sceneColor, sceneGrayScale, Imgproc.COLOR_RGB2GRAY);

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

        //create a bitmap with the grayscale image
        Bitmap bmp = Bitmap.createBitmap(sceneGrayScale.cols(), sceneGrayScale.rows(), Bitmap.Config.RGB_565);

        //get array of points from corners (filled in by the algorithm)
        Point[] points = corners.toArray();

        for (Point p : points) {
            //what is core?
            Imgproc.circle(sceneColor, p, 5, circleColor);
        }

        try {
            Utils.matToBitmap(sceneColor, bmp);
        }

        catch (Exception e) {
            Log.e(this.getClass().getSimpleName(), "Exception thrown: " + e.getMessage());
            bmp.recycle();
        }

        return bmp;
    }

    @Override
    public void run() {
        super.run();

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
}

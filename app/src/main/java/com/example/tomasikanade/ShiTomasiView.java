package com.example.tomasikanade;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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

public class ShiTomasiView extends BridgeView {
    private final String TAG = "ShiTomasiView";
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
    private SurfaceHolder mHolder;


    public ShiTomasiView(Context context, int cameraId, Mat color, Mat grayscale) {
        super(context, cameraId, color, grayscale);
        this.sceneColor = color;
        this.sceneGrayScale = grayscale;
        mHolder = getHolder();
    }

    @Override
    protected Bitmap processFrame(VideoCapture capture) {
        return null;
    }
}

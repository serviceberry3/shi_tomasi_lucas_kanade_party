package com.example.tomasikanade;

import org.opencv.core.Point;

public class KeyFeature {
    private Point pt;
    private double xDiff;
    private double yDiff;

    //the overall "vector" of displacement (just the length of hypotenuse of displacement triangle)
    private double dispVect;


    public KeyFeature(Point coords, double xDisp, double yDisp, double totalDisp) {
        this.pt = coords;
        this.xDiff = xDisp;
        this.yDiff = yDisp;
        this.dispVect = totalDisp;
    }

    public Point getPt() {
        return pt;
    }

    public double getxDiff() {
        return xDiff;
    }

    public double getyDiff() {
        return yDiff;
    }

    public double getDispVect() {
        return dispVect;
    }
}

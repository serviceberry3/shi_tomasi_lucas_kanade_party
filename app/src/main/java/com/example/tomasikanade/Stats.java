package com.example.tomasikanade;

import android.util.Log;

public class Stats {
    //a scratch space to hold indices of medians that we're working with
    public double med = -1;

    /**
     * Find the median displacement of an array of KeyFeatures
     *
     * @param features the array of KeyFeatures of which we want to find the median displacement
     * @param l the index in the array of KeyFeatures at which to start
     * @param n the index at which to end + 1
     * @return the median found
     */
    public float median(KeyFeature[] features, int l, int n)
    {
        //no data
        if (n - l == 0) {
            //return will only be null if there's not enough data
            return -1;
        }

        else if (n-l < 0) {
            //something's wrong
            Log.e("STATSDBUG", "Something's wrong in stats");
            return -1;
        }

        else if (n-l==1) {
            return (float)features[l].getDispVect();
        }

        else if (n-l==2) {
            //if two data, return the average
            return (float)((features[l].getDispVect() + features[l + 1].getDispVect()) / 2);
        }

        //check if the amount of data is even
        else if ((n-l) % 2 == 0) {
            int half = (n - l) / 2;

            this.med = ((double)half + (double)half - 1) / (double)2;
            //average the middle two values
            return (float)(features[l + half].getDispVect() + features[l + half-1].getDispVect())/2;
        }

        //otherwise the amount of data is odd
        else {
            int half = (n - l) / 2;

            this.med = half;

            //Log.i("STATDBUG", String.format("l is %d, n is %d, half computed as %d", l, n, half));

            return (float)features[l + half].getDispVect();
        }
    }

    //calculate the interquartile range for a set of displacement data
    public float[] IQR(KeyFeature[] features, int n, int start)
    {
        int q1RtInd;
        int q3LeftInd;

        //Log.i("STATDBUG", String.format("IQR called with n as %d", n));
        float[] ret = new float[3];

        if (n - start <= 2) {
            //don't calculate IQR unless have at least 3 data
            return null;
        }

        //get median of data
        float medFound = median(features, start, n);
        //Log.i("STATDBUG", String.format("median found to be %f, med set to %f", medFound, med));

        //check if med contains a .5
        if ((med / 0.5) % 2 == 0) {
            //Log.i("STATDBUG", "Median index is whole num");
            //does not contain a .5, so our quarter 1 right index is just (med-1) and quarter 3 left index is (med+1)
            q1RtInd = (int)med - 1;
            q3LeftInd = (int)med + 1;
        }
        else {
            //does contain a .5, so our quarter 1 right index is (med-.5), quarter 3 left index is (med+.5)
            q1RtInd = (int)(med - 0.5);
            q3LeftInd = (int)(med + 0.5);
        }

        //get median of first half of data
        float Q1 = median(features, start, start + q1RtInd + 1);
        //Log.i("STATDBUG", String.format("q1 found to be %f", Q1));

        //get median of second half
        float Q3 = median(features, start + q3LeftInd, n);
        //Log.i("STATDBUG", String.format("q3 found to be %f", Q3));

        //IQR calculation
        ret[0] = Q1;
        ret[1] = Q3;
        ret[2] = Q3-Q1;

        return ret;
    }

    /**
     *
     * @param features the list of KeyFeatures for which outliers need to be erased
     * @param highCutoff the high outlier cutoff
     */
    public void rmOutliers(KeyFeature[] features, float highCutoff, int start, int end) {
        //iterate over each KeyFeature passed; if it's an outlier then invalidate it
        for (KeyFeature keyFeature : features) {
            if (keyFeature.getDispVect() >= highCutoff) {
                keyFeature.setValid(0);
            }
        }
    }
}

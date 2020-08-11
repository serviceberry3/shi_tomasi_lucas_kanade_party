package com.example.tomasikanade;

import java.lang.reflect.Array;
import java.security.Key;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/*
Jenks algo seeks to minimize each class’s average deviation from the class mean, while maximizing each class’s deviation from the
means of the other groups. So it tries to reduce the variance within groupings and maximize the variance between groupings
 */
public class Jenks {
    private KeyFeature[] featureList;

    /*all values should already be in the KeyFeature array
    public void addValue(double value) {
        list.add(value);
    }

    public  void addValues(double... values) {
        for (double value : values) {
            addValue(value);
        }
    }*/


    /*
    public Breaks computeBreaks() {
        int uniqueValues = countUnique();
        if (uniqueValues <= 3) {
            return computeBreaks(list, uniqueValues);
        }

        Breaks lastBreaks = computeBreaks(list, 2);
        double lastGvf = lastBreaks.gvf();
        double lastImprovement = lastGvf - computeBreaks(list, 1).gvf();

        for (int i = 3; i <= Math.min(6, uniqueValues); ++i) {
            Breaks breaks = computeBreaks(list, 2);
            double gvf = breaks.gvf();
            double marginalImprovement = gvf - lastGvf;
            if (marginalImprovement < lastImprovement) {
                return lastBreaks;
            }
            lastBreaks = breaks;
            lastGvf = gvf;
            lastImprovement = marginalImprovement;
        }

        return lastBreaks;
    }
     */

    /*array of KeyFeatures should already be sorted upon arrival
    private  double[] toSortedArray() {
        double[] values = new double[this.list.size()];
        for (int i = 0; i != values.length; ++i) {
            values[i] = this.list.get(i);
        }
        Arrays.sort(values);
        return values;
    }
     */

    /*
    private  int countUnique(double[] sortedList) {
        int count = 1;
        for (int i = 1; i < sortedList.length; ++i) {
            if (sortedList[i] != sortedList[i - 1]) {
                count++;
            }
        }
        return count;
    }
     */

    /**
     * @param numclass int number of classes
     * @return int[] breaks (upper indices of class)
     */
    public Breaks computeBreaks(int numclass) {
        return computeBreaks(featureList, numclass, new Identity());
    }

    //another function for if you want to pass your own list instead of using the one created upon construction of the Jenks object
    private Breaks computeBreaks(KeyFeature[] list, int numclass) {
        return computeBreaks(list, numclass, new Identity());
    }

    /**
     * Do actual computation for the Jenks natural breaks optimization
     *
     * @param list the array of all KeyFeatures to process
     * @param numclass the desired number of groupings as requested by the user
     * @param transform a function performed on a double
     * @return
     */
    private Breaks computeBreaks(KeyFeature[] list, int numclass, DoubleFunction transform) {
        //get the number of data we have (here it's number of KeyFeatures)
        int numdata = list.length;

        //if there are no data, return empty array of KeyFeatures and indices
        if (numdata == 0) {
            return new Breaks(new KeyFeature[0], new int[0]);
        }

        //initialize two 2 arrays of doubles
        double[][] mat1 = new double[numdata + 1][numclass + 1];
        double[][] mat2 = new double[numdata + 1][numclass + 1];

        //iterate over the number of groupings requested
        for (int i = 1; i <= numclass; i++) {
            //fill in second column of mat1 with 1s
            mat1[1][i] = 1;

            //fill in second column of mat2 with 0s
            mat2[1][i] = 0;

            //iterate over the number of total data we have
            for (int j = 2; j <= numdata; j++) {
                //starting at third row of mat2, fill in the whole thing with basically infinity
                mat2[j][i] = Double.MAX_VALUE;
            }
        }

        //initialize a double v as 0
        double v = 0;

        //main processing loop - iterate from 2 up through the number of KeyFeatures we hvae
        for (int l = 2; l <= numdata; l++) {

            //initialize three doubles to 0: s1, s2, and w
            double s1 = 0, s2 = 0, w = 0;

            //iterate from 1 through l (l is the which datum we're on?)
            for (int m = 1; m <= l; m++) {
                //get the difference between l and m
                int i3 = l - m;

                //get the displacement from this feature, index l - m
                double val = transform.apply(list[i3].getDispVect());

                //square this displacement and add it to s2
                s2 += val * val;

                //add the displacement to s1
                s1 += val;

                //increment w
                w++;

                //calculate (the cumulative displacement sum)^2 / the iteration # (starts at 1) and subtract that from (the cumulative sum
                //of squared displacements)
                v = s2 - (s1 * s1) / w;

                //calculate l-m-2
                int i4 = i3 - 2;


                if (i4 != 0) {
                    for (int j = 2; j <= numclass; j++) {
                        if (mat2[l][j] >= (v + mat2[i4][j - 1])) {

                            mat1[l][j] = i3;
                            mat2[l][j] = v + mat2[i4][j - 1];
                        }
                    }
                }
            }

            mat1[l][1] = 1;
            mat2[l][1] = v;

        }

        int k = numdata;

        //create new array of ints to store the breaks
        int[] kclass = new int[numclass];

        //the last break will always be length of the KeyFeature list-1 (the index of the last KeyFeature)
        kclass[numclass - 1] = list.length - 1;

        //iterate from the number of classes down thru 2
        for (int j = numclass; j >= 2; j--) {
            //get the break by subtracting 2 from value at row k, column j
            int id = (int) (mat1[k][j]) - 2;

            //store this break in the breaks array
            kclass[j - 2] = id;

            //change k to the value at row k, column j - 1
            k = (int) mat1[k][j] - 1;
        }

        //make a new instance of Breaks with the appropriate breaks we found and return it
        return new Breaks(list, kclass);
    }

    //interface for abstraction to define a function we want to perform on the passed double
    private interface DoubleFunction {
        double apply(double x);
    }

    private static class Log10 implements DoubleFunction {

        @Override
        public double apply(double x) {
            return Math.log10(x);
        }
    }

    //implement our mini interface
    public static class Identity implements DoubleFunction {
        @Override
        public double apply(double x) {
            //just return the passed value
            return x;
        }
    }

    //a subclass called Breaks to hold the break indices found by the algorithm
    public static class Breaks {
        //the sorted array of KeyFeatures
        private KeyFeature[] sortedFeatures;

        //the break indices found by Jenks algo
        public int[] breaks;

        /**
         * @param sortedFeatures the complete array of sorted data values
         * @param breaks       the indexes of the values within the sorted array that begin new classes (the breakpoints)
         */
        private Breaks(KeyFeature[] sortedFeatures, int[] breaks) {
            this.sortedFeatures = sortedFeatures;
            this.breaks = breaks;
        }

        /**
         * The Goodness of Variance Fit (GVF) is found by taking the difference
         * between the squared deviations from the array mean (SDAM) and the
         * squared deviations from the class means (SDCM), and dividing by the
         * SDAM
         *
         * @return
         */
        public double gvf() {
            //define double sdam as the sum of squared deviations from the mean list of ALL KeyFeatures we're working with here
            double sdam = sumOfSquareDeviations(sortedFeatures);

            //define double sdcm as 0 to start
            double sdcm = 0.0;

            //iterate over all the different groupings
            for (int i = 0; i != numClasses(); ++i) {
                //sum up the sums of square deviations from just each grouping separately
                sdcm += sumOfSquareDeviations(classList(i));
            }

            //divide difference between total array value and by-grouping value by total array value to get GVF value
            return (sdam - sdcm) / sdam;
        }

        //find the sum of the deviations from mean of all the KeyFeatures in the passed array
        private double sumOfSquareDeviations(KeyFeature[] features) {
            //get mean displacement of all the key feature points passed
            double mean = mean(features);

            //start off the sum at 0
            double sum = 0.0;

            //iterate through
            for (int i = 0; i != features.length; ++i) {
                //get this feature's displacement's variation from the mean, and square it
                double sqDev = Math.pow(features[i].getDispVect() - mean, 2);

                //add this square deviation to the sum
                sum += sqDev;
            }
            return sum;
        }

        //convenience API to retrieve the list of KeyFeatures from this Breaks object
        public KeyFeature[] getValues() {
            return sortedFeatures;
        }


        /**
         * Get the list of all KeyFeatures in this specific Jenks grouping
         *
         * @param i the index into the breaks array indicating which grouping list we want
         * @return the list of all KeyFeatures in this specific Jenks grouping
         */
        private KeyFeature[] classList(int i) {
            //get starting index for where to start copying from sortedFeatures. If i == 0, this ind is just 0
            //else the index needs to be retrieved from the breaks array at i-1 since that's where we'll find index of end of last class
            // Notice that we have to add 1 because the break values indicate index where the grouping ENDS, so +1 is where next class starts
            int classStart = (i == 0) ? 0 : breaks[i - 1] + 1;

            //get the last index we need to copy from, which is just the value in breaks at i (the values in breaks indicate at what index
            //the grouping ENDS, so break value at i is exactly what we need)
            int classEnd = breaks[i];

            //create a new array of KeyFeatures of the proper size (add 1 because classEnd and classStart are the index values)
            KeyFeature[] list = new KeyFeature[classEnd - classStart + 1];

            //iterate over the requested patch of sortedFeatures and copy all those features into temp list
            for (int j = classStart; j <= classEnd; ++j) {
                //do copy
                list[j - classStart] = sortedFeatures[j];
            }
            return list;
        }

        /**
         * Get the minimum displacement value of all KeyFeatures in the specified grouping
         *
         * @param classIndex
         * @return the minimum value (inclusive) of the given class
         */
        public double getClassMin(int classIndex) {
            if (classIndex == 0) {
                return sortedFeatures[0].getDispVect();
            }

            else {
                return sortedFeatures[breaks[classIndex - 1] + 1].getDispVect();
            }
        }

        /**
         * Get the maximum displacement value of all KeyFeatures in the specified grouping
         *
         * @param classIndex
         * @return the maximum value (inclusive) of the given class
         */
        public  double getClassMax(int classIndex) {
            return sortedFeatures[breaks[classIndex]].getDispVect();
        }


        /**
         * Convenience API function to get how many KeyFeatures there are in the specified grouping
         *
         * @param classIndex
         * @return
         */
        public  int getClassCount(int classIndex) {
            if (classIndex == 0) {
                return breaks[0] + 1;
            }

            else {
                return breaks[classIndex] - breaks[classIndex - 1];
            }
        }

        /**
         * Get the mean displacement value from an array of KeyFeatures
         *
         * @param features the array of KeyFeatures from which we want to calculate mean displacement
         */
        private double mean(KeyFeature[] features) {
            //start off the sum at 0
            double sum = 0;

            //add up all the displacement values
            for (int i = 0; i != features.length; ++i) {
                sum += features[i].getDispVect();
            }

            //return average
            return sum / features.length;
        }

        //convenience API to retrieve number of breaks attached to this Breaks object (length of this.breaks)
        public int numClasses() {
            return breaks.length;
        }


        @Override
        public  String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i != numClasses(); ++i) {
                if (getClassMin(i) == getClassMax(i)) {
                    sb.append(getClassMin(i));
                } else {
                    sb.append(getClassMin(i)).append(" - ").append(getClassMax(i));
                }
                sb.append(" (" + getClassCount(i) + ")");
                sb.append(" = ").append(Arrays.toString(classList(i)));
                sb.append("\n");
            }
            return sb.toString();
        }

        public  String printClusters() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i != numClasses(); ++i) {
                if (getClassMin(i) == getClassMax(i)) {
                    sb.append(getClassMin(i));
                } else {
                    sb.append(getClassMin(i)).append(" - ").append(getClassMax(i));
                }
                sb.append(" (" + getClassCount(i) + ");");
                // sb.append("\n");
            }
            return sb.toString();
        }

        public  int classOf(double value) {
            for (int i = 0; i != numClasses(); ++i) {
                if (value <= getClassMax(i)) {
                    return i;
                }
            }
            return numClasses() - 1;
        }
    }
}

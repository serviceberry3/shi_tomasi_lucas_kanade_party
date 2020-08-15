package com.example.tomasikanade;

import android.util.Log;

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
    private String TAG = "JENKSDBUG";
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

    public Jenks(KeyFeature[] featureList) {
        this.featureList = featureList;
    }

    /**
     * @param numclass int number of classes
     * @return int[] breaks (upper indices of class)
     */
    public Breaks computeBreaks(int numclass) {
        return computeBreaks(featureList, numclass, new Identity());
    }

    //another function for if you want to pass your own list instead of using the one created upon construction of the Jenks object
    public Breaks computeBreaks(KeyFeature[] list, int numclass) {
        return computeBreaks(list, numclass, new Identity());
    }

    /**
     * Do actual computation for the Jenks natural breaks optimization
     *
     * @param features the array of all KeyFeatures to process
     * @param numclass the desired number of groupings as requested by the user
     * @param transform a function performed on a double
     * @return
     */
    private Breaks computeBreaks(KeyFeature[] features, int numclass, DoubleFunction transform) {
        //get the number of data we have (here it's number of KeyFeatures)
        int numdata = features.length;
        //Log.i(TAG, String.format("Mr. Jenks found %d data", numdata));

        //if there are no data, return empty array of KeyFeatures and indices
        if (numdata == 0) {
            return new Breaks(new KeyFeature[0], new int[0]);
        }

        //initialize two 2D arrays of doubles

        //mat1 will be used to store/sort the indices (breakpoints) as we're finding the ideal ones
        double[][] mat1 = new double[numdata + 1][numclass + 1];

        //mat2 will be used to store/compare the sum of deviation squares for the test groupings
        double[][] mat2 = new double[numdata + 1][numclass + 1];


        //**We can say for mat2 that our vertical axis label is y number of groups, and the horizontal axis label is "for the first x values", and at the end of the
        //algorithm any given cell holds the minimum SCDM_ALL when splitting the first x values of the passed array into y number of groups. Thus the final SCDM_ALL
        //found for the optimal splitting should be located at the cell (numdata, numclass)

        //**We can say something similar for mat1, but it's a bit different. Once the algorithm runs, a column x will hold the ideal [numclass-1] breaks for the first x values.
        //All these breaks, however, need to be added with (-2) in order to be corrected. We'll find the final ideal break indices in column [numdata].

        //iterate over the number of groupings requested
        for (int i = 1; i <= numclass; i++) {
            //fill in second column of mat1 with 1s
            mat1[1][i] = 1;
            //Log.i(TAG, String.format("Initialized mat1[1][%d] to 1", i));

            //fill in second column of mat2 with 0s
            mat2[1][i] = 0;

            //iterate over the number of total data we have
            for (int j = 2; j <= numdata; j++) {
                //starting at third row of mat2, fill in the whole thing with basically infinity
                mat2[j][i] = Double.MAX_VALUE;
            }
        }

        //initialize a double v as 0 to use for calculations of the sum of squared deviations and for copying
        double v = 0;

        //Main processing loop - iterate from 2 up through the number of KeyFeatures we have
        //Significance: with a sorted array of n numbers, if we start with a new array of 2 of those numbers, the number of times we'll need to run calculations on the array
        //and then copy in the next number from original array is equal to n - 1.

        //On each iteration of l, we add a displacement number from features to our array and calculate the best arrangement of the array into numclass groups, and
        //we keep calculating the best arrangement after each addition of a number. That way we can store all of the SDCM data of every possible subgroup and use it for
        //comparisons. Hence the 2D arrays.
        for (int value_addition_it_num = 2; value_addition_it_num <= numdata; value_addition_it_num++) { //note that if we only pass a KeyFeature that has 1 data pt,
                                                                                                        //this whole loop will be skipped b/c val_addition_it_num starts off
                                                                                                        //greater than numdata
            //Log.i(TAG, String.format("Jenks running value_addition_it_num iteration #%d", value_addition_it_num));
            //FOR EACH SUBSET containing #s from indices 0 thru (l-1) of the original array

            //Initialize three doubles to 0: s1, s2, and w
            double cum_sum = 0, cum_sum_of_squares = 0, num_vals = 0;

            //iterate from 1 through l (l is which datum we're on?)
            //SIGNIFICANCE: FINDING BEST POSSIBLE SUB-ARRANGEMENT FOR THIS GROUPING s.t. SDCM_ALL is minimized for the grouping
            for (int sub_arrangement_num = 1; sub_arrangement_num <= value_addition_it_num; sub_arrangement_num++) {
                //Get the difference between l and m. This is the index for where in the grouping we want to set the break this time, plus two
                int test_break_at_plus_two = value_addition_it_num - sub_arrangement_num + 1; //starts at value of l, decrements each iteration of m

                //get the displacement from this feature, index l - m. So the first grouping test will be feat 1 and 0, then 2 1 0, etc
                double val = transform.apply(features[test_break_at_plus_two - 1].getDispVect()); //val starts as features

                //square this displacement and add it to s2. So we're using s2 to sum the squares of the numbers in this "test grouping"
                cum_sum_of_squares += val * val;

                //add the displacement to s1. So we're using s2 to sum the numbers in this "test grouping"
                cum_sum += val;

                //increment num_vals, which is the amount of values we've looked at in this group (it's represented as n in my proof)
                num_vals++;

                //Calculate (the cumulative displacement sum)^2 / the iteration # (starts at 1) and subtract that from (the cumulative sum
                //of squared displacements)
                //cum_sum_of_squares is the sum of the squares of all the values thus far
                //the part in parentheses is sum of the values in the grouping squared, divided by the number of vals in the grouping
                //so v is the cumulative squared deviations sum for this single "test grouping"
                    //this is true because the sum of any n numbers, squared, then divided by n, (this is s1*s1/w)
                    //is equal to the mean of the numbers, squared, multiplied by n
                    //see the quick proof I did in /algo/deviation_squares.pdf
                //GOAL: make the groupings such that sum of v for all groupings is lowest
                v = cum_sum_of_squares - ((cum_sum * cum_sum) / num_vals); //always zero on the first iteration of the m loop within the l loop, since one value squared divided
                //by 1 will always equal the value squared

                //calculate l-m-2, the index of the column in mat2 where the SCDM of the last test grouping is stored
                int scdm_finder_for_left_groupings = test_break_at_plus_two - 1; //this starts at value of l-1, decrements each iteration of m

                //Make sure scdm_finder_for_left_grouping is not zero. If it is, that means i3 was 1, so we selected the value at index 0 of featureList,
                //and this is last iteration of m loop for this l loop, so we can jump straight to storing the final SDCM for this whole test grouping in row 1 of mat2

                //In other words, if it's 0, there's no work to do here because we have just calculated the SDCM for [1st, ..., lth] and there are
                //no more sub-grouping patterns to try
                if (scdm_finder_for_left_groupings != 0) {
                    //Iterate from 2 to the number of groupings requested
                    for (int num_groups_to_find_min_sdcm_for = 2; num_groups_to_find_min_sdcm_for <= numclass; num_groups_to_find_min_sdcm_for++) {
                        //Add v (SDCM for this test sub-grouping) to the sum of squared deviations from the last test groupings (which are at mat2[i4][j-1],
                        //mat2[i4 - 1][j-1], mat2[i4 - 2][j-1], ... , mat2[1][j-1]) and then compare that against the value in mat2 at column l, row 2
                        //which holds a running min for SDCM_ALL of our test sub-arrangements for this larger grouping
                        //
                        //So on each iteration of m we'll be testing a different arrangement of
                        //subgroupings within the bigger grouping ([1st], [1st, 2nd], ... , [1st...lth]) that we're looking at on this iteration of l

                        //If numclass is 2, we'll obviously only be adding this test sub-grouping's SDCM with that of the what's left on the left side. If numclass
                        //is greater than 2 (let's say it's 3 and we have 4 numbers and sub_arrangement_num = 1, value_addition_it_num = 4),  we'll need to have another
                        // j iteration to be able to sum this
                        // test sub-grouping's (just the 4th num) SCDM with the MIN SCDM_ALL found for 2-group arrangements for the previous 3 numbers (which was computed
                        // on the last iteration of value_addition_it_num and is located one
                        // cell above and to the left [column scdm_finder_for_left_groupings, row j-1]), and
                        // then compare that with the running min found for 3 groups for the first 4 values (located at mat2[4][3] for example), then on the next
                        // sub_arrangement_num iteration
                        // we'd use that extra j iteration
                        //to sum this test sub-grouping's SCDM with the MIN SCDM_ALL found for 2-group arrangements for the first two numbers (which was computed two
                        //iteration of value_addition_it_num ago
                        // is located one cell above, two
                        // to the left [column scdm_finder_for_left_groupings, row j-1]), then
                        //compare that with the running min found for 3 groups for the first 4 values (located at mat2[4][3]), etc.

                        //Remember, goal is to have SDCM_ALL be the lowest because that implies minimum variance within groups
                        if (mat2[value_addition_it_num][num_groups_to_find_min_sdcm_for] /*running min for SDCM_ALL for arranging this grouping into [num_groups_to_find_min_sdcm_for] groups.
                                            (Was initialized to infinity).
                                            So for a given iteration of l it starts at infinity, then surely gets replaced by SDCM_ALL for
                                            [(1st), ... , (l-2)th, (l-1)th] [lth], and might be replaced more */ >=
                                (v + mat2[scdm_finder_for_left_groupings][num_groups_to_find_min_sdcm_for - 1]) /*SDCM_ALL for this sub-arrangement: [(1st), ..., (i4th)] [(i4 + 1)th, ... , (l-1)th, lth]*/
                        ) {
                            //Store the test breakpt, the current best break index found for this larger grouping starting from the right, in column l row j of mat1. This means we'll keep
                            //overriding col #[value_addition_it_num] at row #[num_groups_to_find_min_scdm] with the far-right break index that we found to be best when grouping the
                            //[value_addition_it_num] first numbers of the full array into groups of [num_groups_to_find_min_scdm_for]
                            mat1[value_addition_it_num][num_groups_to_find_min_sdcm_for] = test_break_at_plus_two; //thus for each iteration of j 2 or above, we go through and find [numclass] breaks

                            //Replace/override slot in row #[num_groups_to_find_min_sdcdm_for] of mat2 with the min SDCM_ALL we've found for this test group (the set of the first [value_addition_it_num]
                            //values in the full array) thus far
                            mat2[value_addition_it_num][num_groups_to_find_min_sdcm_for] =
                                    v /*Sum of squared deviations (SDCM) of this sub-grouping*/
                                    + mat2[scdm_finder_for_left_groupings][num_groups_to_find_min_sdcm_for - 1] /*SDCM for the set of the first [value_addition_it_num - sub_arrangement_num] values
                                                                                                                in the full array*/;
                        }
                    }
                }
                //otherwise we're done with this value addition iteration
                else {
                    //Log.i(TAG, "i4 came up 0, there's a problem");
                }
            }

            //fill in row 1 of mat1 at column l with a 1 (indicating complete?)
            mat1[value_addition_it_num][1] = 1;

            //fill in row 1 of mat2 at column l with the calculated SDCM for this single possible grouping
            //SIGNIFICANCE: we set the SDCM value to beat for next iteration of l to the SDCM of this entire grouping we looked at
            mat2[value_addition_it_num][1] = v;
        }

        //set k to the num of data since that column of mat1 will contain the first breakpoint from the right that we found to be "good"
        int k = numdata;

        //create new array of ints to store the breaks
        int[] breakpoints = new int[numclass];

        //the last break will always be length of the KeyFeature list-1 (the index of the last KeyFeature)
        breakpoints[numclass - 1] = features.length - 1; //if we only passed 1 datum, we'll have array of 2 ints, the second one is set to 0 here

        //iterate from the number of classes down thru 2, because we'll have [numclass-1] breaks to store
        for (int j = numclass; j >= 2; j--) {
            //get the break by subtracting 2 from value at row k, column j, which is test_break_at_plus_two, hence the -2
            //The first value extracted at mat1[k][j] will be mat1[numdata][numclass], which is where we stored the first ideal breakpoint from the right when grouping the full array into numclass grps
            int id = (int) (mat1[k][j]) - 2; //EDGE CASE: only 1 data pt, 2 or more classes requested: the extracted breakpoint is mat1[1][# classes req] - 2 = -1

            //Log.i(TAG, String.format("id is %d from mat1[%d][%d]", id, k, j));

            //EDGE CASE: if at any point we get a -1 for id, we know that # of data passed was less than # classes requested. return null immediately
            if (id == -1) {
                return null;
            }

            //store this break in the breaks array, starting at the second to last slot
            breakpoints[j - 2] = id; //EDGE CASE: only 1 data pt: now the first slot of the array will be set to Double.MAX. Will appear as -1 if try to read as int

            //On the next iteration we need to find the second ideal breakpoint from the right when grouping the full array into numclass grps. In other words, we need the best breakpt found
            //first from the right when splitting the first [id + 1] values of the full array into [j - 1] groups. j will automatically be decremented after this iteration of the loop, but we
            //need to change k to id + 1. The +1 is due to the fact that the breakpoint values correspond to the last INDEX of a given group, but we're looking for a number of values, not an index
            k = (int) mat1[k][j] - 1;
        }

        //make a new instance of Breaks with the appropriate breaks we found and return it
        return new Breaks(features, breakpoints);
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
                sdcm += sumOfSquareDeviations(getAllFeaturesInGrouping(i));
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
        private KeyFeature[] getAllFeaturesInGrouping(int i) {
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
        public double getClassMax(int classIndex) {
            return sortedFeatures[breaks[classIndex]].getDispVect();
        }


        /**
         * Convenience API function to get how many KeyFeatures there are in the specified grouping
         *
         * @param classIndex
         * @return
         */
        public int getClassCount(int classIndex) {
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
        public static double mean(KeyFeature[] features) {
            //start off the sum at 0
            double sum = 0;

            //add up all the displacement values
            for (int i = 0; i != features.length; ++i) {
                sum += features[i].getDispVect();
            }

            //return average
            return sum / features.length;
        }

        /**
         * Get the mean displacement value from a specified subset of an array of KeyFeatures
         *
         * @param features the array of KeyFeatures to be analyzed
         * @param start the starting index for mean extraction
         * @param end the ending index for mean extraction
         *
         * @return
         */
        public static double mean(KeyFeature[] features, int start, int end) {
            double sum = 0;

            //add up all the displacement values
            for (int i = start; i <= end; i++) {
                sum += features[i].getDispVect();
            }

            //return average
            return sum / (end - start + 1);
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
                sb.append(" = ").append(Arrays.toString(getAllFeaturesInGrouping(i)));
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

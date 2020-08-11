package com.example.tomasikanade;

public class ValueCountPair {
    // the value
    private double value;
    // occurrence counter
    private int count;

    public ValueCountPair(double value, int count) {
        this.value = value;
        this.count = count;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void incCount(){
        this.count++;
    }
}

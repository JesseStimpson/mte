package com.bandwidwith.rw.mte;

public class MaxIsBetter implements Sorter {

    @Override
    public double getBest(double x, double y) {
        return Math.max(x, y);
    }

    @Override
    public double getWorst(double x, double y) {
        return Math.min(x, y);
    }

}

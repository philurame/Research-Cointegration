package com.improve.trading.strategy.service;


import lombok.Getter;
import lombok.Setter;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;

public class MAComputer {

    double[] values;
    int size;
    int actual_size = 0;
    int position = -1;
    int intervalMs;
    double totalSum = 0;
    double squareSum = 0;
    long lastTimestampMs = 0;
    boolean isReady = false;
    public double lastValue = Double.NaN;
    public double ewmValue = Double.NaN;
    int maDeltaSign = 0;
    int ewmDeltaSign = 0;
    @Getter
    @Setter
    boolean isMaTouched = false;
    @Getter
    @Setter
    boolean isEwmTouched = false;

    double stdCached = Double.NaN;
    long lasStdUpdate = 0;

    public MAComputer(int size, int intervalMs) {
        values = new double[size];
        this.size = size;
        this.intervalMs = intervalMs;
    }

    public void updateEwm(double v) {
        if (Double.isNaN(ewmValue)) {
            ewmValue = v;
        } else {
            // pandas ewm span, adjust=False
            double alpha = 2.0 / (1.0 + size);
            ewmValue = (1 - alpha) * ewmValue + alpha * v;
        }
    }

    public void warmup(double[] x, long ts) {

        actual_size = Math.min(x.length, size);
        System.arraycopy(x, x.length - actual_size, values, 0, actual_size);

        for (int j = 0; j < actual_size; ++j) {
            totalSum = values[j];
            squareSum = values[j] * values[j];
            updateEwm(values[j]);
        }

        position = actual_size - 1;
        lastTimestampMs = ts;
        isReady = true;
    }

    public void update(double x, long ts) {
        if (!isReady)
            return;
        if (ts - lastTimestampMs > intervalMs) {
            lastTimestampMs = ts;
            position = (position + 1) % size;
            actual_size = Math.min(size, actual_size + 1);
            updateEwm(x);
            totalSum -= values[position];
            squareSum -= values[position] * values[position];
            totalSum += x;
            squareSum += x * x;
            values[position] = x;
        }
        lastValue = x;
        int newMaDeltaSign = (lastValue - totalSum / actual_size) > 0 ? 1 : -1;
        if (newMaDeltaSign != maDeltaSign) {
            isMaTouched = true;
            maDeltaSign = newMaDeltaSign;
        }
        int newEwmDeltaSign = (lastValue - ewmValue) > 0 ? 1 : -1;
        if (newEwmDeltaSign != ewmDeltaSign) {
            isEwmTouched = true;
            ewmDeltaSign = newEwmDeltaSign;
        }
    }

    public double[] getRawData() {
        double[] out = new double[actual_size];
        for (int i = 0; i < actual_size; ++i) {
            out[i] = values[i];
        }
        return out;
    }

    public double getSum() {
        return isReady ? totalSum : Double.NaN;
    }
    public double getSumDebug() {
        if (!isReady) {
            return Double.NaN;
        }
        double sum = 0.0;
        for (int i = 0; i < actual_size; ++i) {
            sum += values[i];
        }
        return sum;
    }

    public double getEwm() {
        return ewmValue;
    }
    public double getMA() {
        return isReady ? totalSum / actual_size : Double.NaN;
    }

    public double getPrevValue() {
        return isReady ? values[(position + size - 1) % size] : Double.NaN;
    }
    public double getStd() {
        if (!isReady) {
            return Double.NaN;
        }
        if (Double.isNaN(stdCached) || (lastTimestampMs > lasStdUpdate + 10_000)) {
            stdCached = getStdSimple();
            lasStdUpdate = lastTimestampMs;
        }
        return stdCached;

        //return isReady ? Math.sqrt(squareSum / actual_size - totalSum * totalSum / (actual_size * actual_size)) : Double.NaN;
    }

    public double getStdSimple() {
        if (!isReady) {
         return Double.NaN;
        }
        double mean = 0.0;
        for (int i = 0; i < actual_size; ++i) {
         mean += values[i];
        }
        mean /= actual_size;
        double std = 0.0;
        for (int i = 0; i < actual_size; ++i) {
        std += (values[i] - mean) * (values[i] - mean);
        }
        std /= actual_size;
        return Math.sqrt(std);
    }

    public double getMASimple() {
        if (!isReady) {
            return Double.NaN;
        }
        double mean = 0.0;
        for (int i = 0; i < actual_size; ++i) {
            mean += values[i];
        }
        mean /= actual_size;

        return mean;
    }



//    public static void main(String[] args) throws IOException, ParseException {
//
//
//        JSONParser parser = new JSONParser();
//
//        Object[] obj = ((JSONArray)parser.parse(
//                new FileReader("spread_example.txt"))
//        ).toArray();
//
//        double[] p = new double[obj.length];
//
//        for (int i = 0 ; i < obj.length; ++i) {
//            p[i] = (double)obj[i];
//        }
//
//
//        int size = 20000;
//        MAComputer r = new MAComputer(size, 5000);
//
//        long ts = Instant.now().toEpochMilli();
//        double[] warmup = new double[size];
//        System.arraycopy(p, 0, warmup, 0, size);
//
//        r.warmup(warmup, ts);
//
//        System.out.println(r.getSum() + " " + r.getSumDebug());
//
//        for (int i = size; i < p.length; ++i) {
//            ts += 10000;
//            r.update(p[i], ts);
//            System.out.println(r.getStd() + " " + r.getStdSimple());
////            System.out.println(r.getSum() + " " + r.getSumDebug());
//        }
//    }

//    public static void main(String[] args) {
//
//        double[] warmupTest = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
//
//        MAComputer r = new MAComputer(5);
//
//        long ts = Instant.now().toEpochMilli();
//        r.warmup(warmupTest, ts);
//        System.out.println(r.getSum());
//        System.out.println(r.getMA());
//        System.out.println(r.getStd());
//        r.update(100, ts);
//        System.out.println(r.getSum());
//        System.out.println(r.getMA());
//        System.out.println(r.getStd());
//        r.update(100, ts + 77000);
//        System.out.println(r.getSum());
//        System.out.println(r.getMA());
//        System.out.println(r.getStd());
//
//        for (int i = 0 ; i < 100000; ++i) {
//            r.update(-100 + i * 0.001, ts);
//            System.out.println(r.getStd());
//            ts += 77000;
//        }
//    }

}

package com.improve.trading.strategy.service;

import lombok.Getter;

import java.util.*;

public class IndexParameters {
    @Getter
    int id;
    @Getter
    String indexName;
    @Getter
    double quoteSize;
    @Getter
    int windowMA;
    @Getter
    boolean useEWM;
    @Getter
    double cEnter;
    @Getter
    double cExit;
    @Getter
    double cStop;
    @Getter
    double stdThreshold;
    @Getter
    List<String> leads;
    @Getter
    List<String> lags;
    @Getter
    HashMap<String, Double> weight = new HashMap<>();

    public IndexParameters(int id, String indexName, double quoteSize, int windowMA, boolean useEWM, double cEnter, double cExit, double cStop, double stdThreshold,
                           String[] leads, String [] lags) {
        this.id = id;
        this.indexName = indexName;
        this.windowMA = windowMA;
        this.useEWM = useEWM;
        this.cEnter = cEnter;
        this.cExit = cExit;
        this.cStop = cStop;
        this.leads = Arrays.asList(leads);
        this.lags = Arrays.asList(lags);
        this.quoteSize = quoteSize;
        this.stdThreshold = stdThreshold;

        for (String l : leads)
            weight.put(l, 1.0);
        for (String l : lags)
            weight.put(l, -1.0);
    }

    public String toString() {
        return String.format(Locale.US, "MA=%d|EWM=%s|enter=%.2f|exit=%.2f|stop=%.2f", windowMA, useEWM, cEnter, cExit, cStop);
    }

}

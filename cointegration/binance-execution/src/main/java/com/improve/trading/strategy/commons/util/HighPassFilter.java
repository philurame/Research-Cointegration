package com.improve.trading.strategy.commons.util;

import lombok.Getter;

public class HighPassFilter {

    final double alpha;

    @Getter
    private double result = 0.0;
    private double prevValue = 0.0;

    public HighPassFilter(double alpha) {
        this.alpha = alpha;
    }

    public double update(final double value) {
        result = alpha * (result + value - prevValue);
        prevValue = value;
        return result;
    }
}

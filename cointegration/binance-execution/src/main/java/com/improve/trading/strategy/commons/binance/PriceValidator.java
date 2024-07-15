package com.improve.trading.strategy.commons.binance;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PriceValidator {

    private final double multiplier;

    private PriceValidator(double multiplier) {
        this.multiplier = multiplier;
    }

    public double validate(double price) {
        return Math.round(price * multiplier) / multiplier;
    }

    public static PriceValidator createByPriceStep(final double priceStep) {
        return new PriceValidator(stepToMultiplier(priceStep));
    }

    private static double stepToMultiplier(double step) {
        double multiplier = BigDecimal.valueOf(1).divide(BigDecimal.valueOf(step), 0, RoundingMode.HALF_UP).doubleValue();//1.0 / step;

        if (!Double.isNaN(multiplier) && multiplier != Math.round(multiplier)) {
            throw new RuntimeException("Invalid step: " + step + " (multiplier = " + multiplier + ")");
        }

        return multiplier;
    }
}

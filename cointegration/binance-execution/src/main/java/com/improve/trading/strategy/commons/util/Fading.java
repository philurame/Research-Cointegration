package com.improve.trading.strategy.commons.util;

/**
 * Класс для реализации медленного затухания значения.
 */
public class Fading {

    private final double fadeSpeed;
    private final double max;

    private double value;
    private long lastMaxTime;

    /**
     * Конструктор.
     *
     * @param max              максимальное значение.
     * @param fadeToZeroTimeMs количество миллисекунд за которое затухание будет падать с максимального значения до 0.
     */
    public Fading(double max, long fadeToZeroTimeMs) {
        this.fadeSpeed = max / fadeToZeroTimeMs;
        this.max = max;
    }

    /**
     * @param value новое значение.
     * @return возвращает исправленное значение, на которое наложено затухание.
     */
    public double put(final double value) {
        long now = System.currentTimeMillis();

        this.value = Math.max(0.0, this.value - (now - lastMaxTime) * fadeSpeed);
        lastMaxTime = now;

        if (value > this.value) {
            this.value = Math.min(max, value);
        }
        return this.value;
    }

    public double get() {
        return this.value;
    }

//    public static void main(String[] args) throws InterruptedException {
//        Fading f = new Fading(1000, 6000);
//        for (int i = 0; i < 30; i++) {
//            double newValue = i < 5 ? 150.0 * i : 0.0;
//            double v = f.put(newValue);
//            System.out.println(i + ";" + newValue + ";" + v);
//            Thread.sleep(100);
//        }
//    }
}

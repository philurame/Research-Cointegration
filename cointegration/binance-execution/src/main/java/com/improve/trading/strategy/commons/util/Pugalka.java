package com.improve.trading.strategy.commons.util;

import com.improve.trading.strategy.service.advisers.CalendarAdviser;

import java.time.Instant;
import java.util.List;

public class Pugalka {

    private final int latencyBound;
    private final int latencyStep;
    private final double startKfLatency;

    public final double minTtvMks;
    public final double maxTtvMks;
    public final double ttvWeight;

    private static final BufferedLogger blog = BufferedLogger.getLogger();

    public double fundingKf = 0.0;
    public double latencyKf = 0.0;
    public double volatilityKf = 0.0;
    public double timeToVolKf = 0.0;
    public double newsKf = 0.0;
    public double totalAdvKf = 0.0;

    private String name;
    private final double minFunding;
    private final double maxFunding;
    private final double fundingWeight;
    private final double minVolatility;
    private final double maxVolatility;
    private final double volatilityWeight;
    private final long befNewReactionMills;
    private final long aftNewReactionMills;
    private final double maxAdvantageKf;
    private final Fading latencyFading;
    private final Fading volatilityFading;


    public Pugalka(String name, double minFunding, double maxFunding, double fundingWeight,
                   StrategySettings settings,
                   int befNewReactionSec, int aftNewReactionSec,
                   double maxAdvantageKf) {
        this.name = name;
        this.latencyBound = settings.minLatency;
        this.latencyStep = settings.stepLatency;
        this.startKfLatency = settings.startKfLatency;
        this.minFunding = minFunding;
        this.maxFunding = maxFunding;
        this.fundingWeight = fundingWeight;
        this.minVolatility = settings.minVolatility;
        this.maxVolatility = settings.maxVolatility;
        this.volatilityWeight = settings.maxVolatylityKf;
        this.minTtvMks = settings.minTtvMks;
        this.maxTtvMks = settings.maxTtvMks;
        this.ttvWeight = settings.maxTtvKf;
        this.befNewReactionMills = befNewReactionSec * 1000L;
        this.aftNewReactionMills = aftNewReactionSec * 1000L;
        this.maxAdvantageKf = maxAdvantageKf;
        this.latencyFading = new Fading(10_000 / latencyStep, settings.fadingLatMs);
        this.volatilityFading = new Fading(settings.maxVolatylityKf, settings.fadingVolatilityMs);
    }

    public void calc(double funding, double latency, double volatility, double timeToVolume, Instant timeNow, List<CalendarAdviser.VolatilityEvent> cc) {
        long ts = timeNow.toEpochMilli();

        if (cc == null) {
            newsKf = 0.0;
        } else {
            double newsWeightSumm = 0.0;
            for (CalendarAdviser.VolatilityEvent ve : cc) {
                long newsTs = ve.timestamp().getTime();
                if (newsTs > ts + befNewReactionMills) break;
                if (newsTs + befNewReactionMills < ts && newsTs - aftNewReactionMills > ts) {
                    double thisNewsWeight;
                    boolean isFutureNews = newsTs > ts;
                    thisNewsWeight = getYLinearSigmoid(Math.abs(newsTs - ts), 0, 1, isFutureNews ? befNewReactionMills : aftNewReactionMills, 0);
                    newsWeightSumm = +(ve.high() * 0.5 + ve.moderate() * 0.01 + ve.low() * 0.005) * thisNewsWeight;
                }
            }
            newsKf = Math.min(1.0, newsWeightSumm) * 100;
        }


        fundingKf = getYLinearSigmoid(funding, 0, fundingWeight, minFunding, maxFunding);
        final double latencyRawKf = latency > latencyBound ? Math.max(startKfLatency, latency / latencyStep) : 0.0; //getYLinearSigmoid(latency, 0, latencyWeight, minLatency, maxLatency);
        latencyKf = latencyFading.put(latencyRawKf);
        blog.info("{} pugalka latency: {} ms, latencyKf {} -> {}", name, latency, latencyRawKf, latencyKf);

        timeToVolKf = getYLinearSigmoid(Math.max(maxTtvMks - timeToVolume, 0.0), 0, ttvWeight, minTtvMks, maxTtvMks);
        final double volatilityRawKf = getYLinearSigmoid(volatility, 0, volatilityWeight, minVolatility, maxVolatility);
        volatilityKf = volatilityFading.put(volatilityRawKf);
        blog.info("{} pugalka volatility: {} ms, volatilityRawKf {} -> {}", name, volatility, volatilityRawKf, volatilityKf);

        totalAdvKf = Math.min(maxAdvantageKf, Math.max(Math.max(Math.max(latencyKf, volatilityKf), newsKf), timeToVolKf) + fundingKf);
    }

    public static double getYLinearSigmoid(double currValue, double y1, double y2, double x1, double x2) {
        double k = (y1 - y2) / (x1 - x2);
        double b = (y2 - k * x2);
        return Math.max(y1, Math.min(y2, k * currValue + b));
    }

}
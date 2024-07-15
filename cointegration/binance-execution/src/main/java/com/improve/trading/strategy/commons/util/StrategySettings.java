package com.improve.trading.strategy.commons.util;

import lombok.Getter;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.time.Duration;

@Getter
@ToString
@Configuration
@PropertySource("classpath:strategy.properties")
@PropertySource("classpath:application.properties")
public class StrategySettings {


    @Value("${strategy.config.name}")
    private String configName;

    @Value("${strategy.extendedLogging:true}")
    private boolean extendedLogging;

    @Value("${strategy.initialDeltasJson:#{null}}")
    public String initialDeltasJson;

    @Value("${strategy.bestAskBidVolInitValueJson:#{null}}")
    public String bestAskBidVolInitValueJson;

    @Value("${strategy.bestAskBidVolSlowInitValueJson:#{null}}")
    public String bestAskBidVolSlowInitValueJson;

    /**
     * Максимально разрешенная позиция. Везде заданная в долларах.
     */
    @Value("${strategy.maxPos}")
    private double maxPos;

    @Value("${strategy.isPassiveMasterOpen:true}")
    private Boolean isPassiveMasterOpen;

    @Value("${strategy.isPassiveMasterOpenAggressive:false}")
    private Boolean isPassiveMasterOpenAggressive;

    @Value("${strategy.gate.OrderBookTimeout}")
    private Duration gateOrderBookTimeout;

    @Value("${strategy.deribit.OrderBookTimeout}")
    private Duration deribitOrderBookTimeout;

    @Value("${strategy.binance.OrderBookTimeout}")
    private Duration binanceOrderBookTimeout;

    @Value("${strategy.marketDataAdviser.minSpreadToTrade:10000000}")
    private double minSpreadToTrade;

    @Value("${strategy.maxLeverageForFullBalance}")
    private double maxLeverageForFullBalance;

    @Value("${strategy.maxLeverageForReducePosition}")
    public double maxLeverageForReducePosition;

    @Value("${strategy.binance.deepMarket}")
    public boolean deepMarket;

    @Value("${strategy.binance.delayToClose}")
    public long delayToClose;

    @Value("${telegram.notifyAboutOrders:true}")
    private boolean notifyAboutOrders;

    @Value("${strategy.fishing.interval}")
    public double fishingInterval;

    @Value("${strategy.fishing.waitAfterUpdate:0ms}")
    public Duration waitAfterUpdate;

    @Value("${strategy.extraValueLeverage}")
    public double extraValueLeverage;

    @Value("${strategy.statisticSaveInterval:1s}")
    public Duration statisticSaveInterval;

    @Value("${strategy.onlyClose}")
    private boolean onlyClose;

    @Value("${strategy.binance.RateLimiterInitializeJson:#{null}}")
    private String rateLimiterInitializeJson;

    @Value("${strategy.isPortfolioMargin:false}")
    private boolean isPortfolioMargin;

    @Value("${strategy.coinName}")
    private String coinName;

    @Value("${strategy.fishing:true}")
    private boolean fishing;
    //Pugalka


    @Value("${strategy.minLatency}")
    public int minLatency;

    @Value("${strategy.stepLatency}")
    public int stepLatency;

    @Value("${strategy.startKfLatency}")
    public double startKfLatency;

    @Value("${strategy.fadingLatMs}")
    public long fadingLatMs;

    @Value("${strategy.fadingVolatilityMs}")
    public long fadingVolatilityMs;

    @Value("${strategy.maxVolatylityKf}")
    public double maxVolatylityKf;

    @Value("${strategy.minVolatility}")
    public double minVolatility;

    @Value("${strategy.maxVolatility}")
    public double maxVolatility;

    @Value("${strategy.minTtvMks}")
    public double minTtvMks;

    @Value("${strategy.maxTtvMks}")
    public double maxTtvMks;

    @Value("${strategy.maxTtvKf}")
    public double maxTtvKf;

    @Value("${strategy.spreadCheck:false}")
    private boolean spreadCheck;
}

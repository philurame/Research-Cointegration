package com.improve.trading.strategy.commons.arbitrage;

import com.improve.trading.api.common.Direction;
import com.improve.trading.api.strategy.Controls;
import com.improve.trading.api.strategy.data.id.AccountId;
import com.improve.trading.api.strategy.data.id.TradingPairId;
import com.improve.trading.common.data.Exchange;
import com.improve.trading.strategy.commons.util.ratelimiter.RateLimiter;
import com.improve.trading.strategy.framework.api.Notifier;
import com.improve.trading.strategy.framework.api.context.MarketContext;
import com.improve.trading.strategy.framework.api.context.OpenOrderListener;
import com.improve.trading.strategy.framework.service.bounce.BounceManager;
import com.improve.trading.strategy.framework.service.fishing.FishingManagerWithoutUpdate;
import com.improve.trading.strategy.framework.service.fishing.FishingWithoutUpdateSettings;
import com.improve.trading.strategy.framework.settings.PriceSizeSettings;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
public class PassiveExecution {

    public final FishingManagerWithoutUpdate slaveFishingManager;
    boolean isSpot;
    double maxPosSlaveReduce = 300;
    boolean onlyCloseThis = false;
    double priceStep;

    public PassiveExecution(Controls controls,
                            AccountId accountId,
                            TradingPairId tradingPairId,
                            RateLimiter slaveRateLimiter,
                            Notifier notifier,
                            OpenOrderListener openOrderListener,
                            PriceSizeSettings slavePriceSizeSettings
                            ) {


        priceStep = slavePriceSizeSettings.getPriceStep();
        isSpot = Exchange.BINANCE.name().equals(accountId.getExchangeId());
        BounceManager slaveBounceManager = BounceManager.create(slaveRateLimiter);
        log.info("PassiveExecution {}", tradingPairId);


//        switch (master1.getExchange()) {
//            case KUCOIN, BINANCE_PORTFOLIO_DELIVERY -> {
//                final FishingWithoutUpdateSettings binanceFishingSettings = new FishingWithoutUpdateSettings(tools1.getPriceSizeSettings(),
//                        0.005,
//                        Duration.ofMillis(500),
//                        Duration.ofMillis(10),
//                        0,
//                        Duration.ofMillis(500), 3, 1);
//                masterFishingManager = new FishingManagerWithoutUpdate(master1.getAccountId(),
//                        master1.getInstrumentId(),
//                        controls,
//                        binanceFishingSettings,
//                        bounceManager,
//                        rateLimiter,
//                        notifier,
//                        tools1.getOpenOrderListener(),
//                        masterTickToCommandTimer);
//            }
//            default -> {
//                FakeOrderManager fakeOrderManager = new FakeOrderManager(
//                        master1.getAccountId(),
//                        master1.getInstrumentId(),
//                        controls,
//                        new FakeSettings(tools1.getPriceSizeSettings().getPriceStep(),
//                                instrument.quoteVolume()
//                        ),
//                        rateLimiter,
//                        notifier);
//                contextEventHandler.addListeners(fakeOrderManager);
//
//                final FishingWithUpdateSettings binanceFishingSettings = new FishingWithUpdateSettings(tools1.getPriceSizeSettings(),
//                        0.005,
//                        Duration.ofMillis(500),
//                        Duration.ofMillis(10),
//                        0.0,
//                        Duration.ofMillis(10));
//                masterFishingManager = new FishingManagerWithUpdate(master1.getAccountId(),
//                        master1.getInstrumentId(),
//                        controls,
//                        binanceFishingSettings,
//                        fakeOrderManager,
//                        null,
//                        rateLimiter,
//                        notifier,
//                        tools1.getOpenOrderListener(),
//                        masterTickToCommandTimer);
//            }
//        }

        slaveFishingManager = new FishingManagerWithoutUpdate(accountId,
                tradingPairId,
                controls,
                new FishingWithoutUpdateSettings(slavePriceSizeSettings, 0.01,
                        Duration.ofMillis(0),
                        Duration.ofMillis(0),
                        0.0,
                        Duration.ofMillis(500),
                        1,
                        1,
                        1),
                slaveBounceManager,
                slaveRateLimiter,
                notifier,
                openOrderListener,
                null);
    }

    public void onEvent(MarketContext slaveContext, double fishingInterval, double price) {

        double slavePositionInUsd = slaveContext.getPositionSize() * slaveContext.getMaxBid();
        double slavePosition = slaveContext.getPositionSize();

        double relPriceStep = priceStep / price;
        double adv = 0.001;
        double correctFishingInterval = Math.max(relPriceStep * 1.1, fishingInterval);
        double correctAdvantage = Math.max(0.01, Math.max(relPriceStep * 1.1, adv));

        slaveFishingManager.setIntervalForMove(correctFishingInterval);
        slaveFishingManager.setIntervalForBestPrice(0);
        double buyPrice = price * (1 - correctAdvantage);
        double sellPrice = price * (1 + correctAdvantage);

        double defaultSize = 200.0 / price;

        if (isSpot) {
            if (Math.abs(slavePositionInUsd) > maxPosSlaveReduce) {
                final double target = (maxPosSlaveReduce / slaveContext.getAvgRate());
                // активно тут
            } else {
                if (!onlyCloseThis) {
                    slaveFishingManager.update(Direction.BUY, buyPrice, Math.abs(defaultSize));
                } else {
                    slaveFishingManager.cancelAllOrders(Direction.BUY);
                }
                if (slavePositionInUsd > 10.5) {
                    slaveFishingManager.update(Direction.SELL, sellPrice, Math.min(slavePositionInUsd, defaultSize));
                } else {
                    slaveFishingManager.cancelAllOrders(Direction.SELL);
                }
                slaveFishingManager.onExecute();
            }
        } else {
            if (slavePositionInUsd >= maxPosSlaveReduce) {
                slaveFishingManager.update(Direction.SELL, sellPrice, defaultSize);
                slaveFishingManager.cancelAllOrders(Direction.BUY);
            } else if (slavePositionInUsd <= -maxPosSlaveReduce) {
                slaveFishingManager.update(Direction.BUY, buyPrice, defaultSize);
                slaveFishingManager.cancelAllOrders(Direction.SELL);
            } else {
                slaveFishingManager.update(Direction.BUY, buyPrice, defaultSize);
                slaveFishingManager.update(Direction.SELL, sellPrice, defaultSize);
            }
            slaveFishingManager.onExecute();
        }


    }
}

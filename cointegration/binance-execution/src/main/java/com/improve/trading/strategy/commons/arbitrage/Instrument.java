package com.improve.trading.strategy.commons.arbitrage;

import com.improve.trading.api.strategy.data.StrategyConfig;
import com.improve.trading.api.strategy.data.id.AccountId;
import com.improve.trading.api.strategy.data.id.TradingPairId;

import java.util.HashMap;

public record Instrument(AccountId accountId, TradingPairId slavePair) {

    public static Instrument create(StrategyConfig.PrimaryConfig config, HashMap<String, AccountId> accounts, final String pair1) {
        for (StrategyConfig.PrimaryPairConfig tradingPair : config.getTradingPairs()) {
            TradingPairId tradingPairId = tradingPair.getPair().getTradingPairId();

            if (tradingPairId.getTradingPairId().startsWith(pair1) || tradingPairId.getTradingPairId().startsWith("1" + pair1)) {
                return new Instrument(accounts.get(tradingPairId.getExchangeId()), tradingPairId);
            }

        }
        throw new IllegalArgumentException("Can not find tradingPairId in config " + pair1);
    }

}

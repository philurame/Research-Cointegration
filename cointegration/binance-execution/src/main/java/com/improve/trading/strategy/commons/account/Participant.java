package com.improve.trading.strategy.commons.account;

import com.improve.trading.api.strategy.data.StrategyConfig;
import com.improve.trading.api.strategy.data.id.AccountId;
import com.improve.trading.api.strategy.data.id.DataFeedId;
import com.improve.trading.api.strategy.data.id.TradingPairId;
import com.improve.trading.common.data.Exchange;
import com.improve.trading.strategy.commons.arbitrage.ParticipantInt;
import lombok.Value;

import java.util.Optional;

@Value
public class Participant implements ParticipantInt {

    Exchange exchange;
    TradingPairId instrumentId;
    AccountId accountId;
    DataFeedId rateFeedId;
    StrategyConfig.TradingPairConfig tradingPairConfig;
    String shortName;

    private Participant(StrategyConfig.TradingPairConfig tradingPairConfig, TradingPairId instrumentId, AccountId accountId, DataFeedId rateFeedId) {
        this.exchange = Exchange.valueOf(accountId.getExchangeId());
        this.instrumentId = instrumentId;
        this.accountId = accountId;
        this.rateFeedId = rateFeedId;
        this.tradingPairConfig = tradingPairConfig;
        this.shortName = getShortName(exchange);
    }

    public static Participant create(StrategyConfig.PrimaryConfig primary, TradingPairId tradingPairId, AccountId slaveAccount) {
        Optional<StrategyConfig.PrimaryPairConfig> first = primary.getTradingPairs().stream().filter(primaryPairConfig -> primaryPairConfig.getPair().getTradingPairId().equals(tradingPairId)).findFirst();
        if (first.isEmpty()) {
            throw new IllegalArgumentException("Can not find config for " + tradingPairId);
        }
        return new Participant(first.get().getPair(), tradingPairId, slaveAccount, null);
    }

    public static String getShortName(Exchange exchange) {
        return switch (exchange) {
            case DERIBIT -> "Deribit";
            case BINANCE, BINANCE_DELIVERY, BINANCE_FUTURES, BINANCE_MARGIN -> "Binance";
            case GATE_FUTURES_BTC, GATE_FUTURES_USD, GATE_FUTURES_USDT, GATE_SPOT -> "Gate";
            case BINANCE_PORTFOLIO_MARGIN, BINANCE_PORTFOLIO_FUTURES, BINANCE_PORTFOLIO_DELIVERY -> "Binance-pm";
            case BITMEX -> "Bitmex";
            case FTX_SPOT, FTX_FUTURES -> "Ftx";
            case UPBIT -> "Upbit";
            case KUCOIN_SPOT -> null;
            case KUCOIN_FUTURES -> "Kucoin";
            case OKX_SPOT, OKX_FUTURES -> "Okx";
            case KRAKEN -> "Kraken";
            case COINBASE -> "Coinbase";
            case CRYPTO_COM -> "CryptoCom";
            case MEXC -> "Mexc";
            case BYBIT_SPOT -> null;
            case BYBIT_FUTURES -> null;
            case BYBIT_DELIVERY -> null;
            case BINGX_SPOT -> null;
            case BINGX_FUTURES -> null;
            case NULL_VAL -> null;
        };
    }
}

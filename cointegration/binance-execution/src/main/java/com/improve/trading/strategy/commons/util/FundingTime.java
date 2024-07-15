package com.improve.trading.strategy.commons.util;

import com.improve.trading.common.data.Exchange;

import java.time.Instant;

public class FundingTime {

    public static Instant get(Exchange exchange) {
        return switch (exchange) {
            case BINANCE_FUTURES, BINANCE_DELIVERY -> Instant.parse("2022-12-01T00:00:00.000Z");
            case GATE_FUTURES_USDT, GATE_FUTURES_BTC, GATE_FUTURES_USD -> Instant.parse("2022-12-01T00:00:00.000Z");
            default -> throw new IllegalArgumentException("Funding time is not defined for " + exchange);
        };
    }
}

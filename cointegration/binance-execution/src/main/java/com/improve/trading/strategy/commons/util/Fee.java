package com.improve.trading.strategy.commons.util;

import com.improve.trading.common.data.Exchange;

public record Fee(double taker, double maker) {

    public static Fee get(Exchange exchange) {
        return switch (exchange) {
            case DERIBIT -> new Fee(0.0003, -0.0001);
            case BINANCE_DELIVERY -> new Fee(0.00024, -0.00023);
            case BINANCE -> new Fee(0.00025, -0.00003);
            case BINANCE_FUTURES -> new Fee(0.00025, -0.00003);
            case BITMEX -> new Fee(0.000175, -0.000125);
            case GATE_FUTURES_USD, GATE_FUTURES_USDT -> new Fee(0.000225, -0.000125);
            case GATE_FUTURES_BTC -> new Fee(0.0000, 0.0000);
            case KUCOIN_FUTURES -> new Fee(0.00060, 0.00020);
            case OKX_FUTURES -> new Fee(0.00050, 0.00020);
            default -> throw new IllegalArgumentException("Fee for exchange " + exchange + " is not defined");
        };
    }
}

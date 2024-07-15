package com.improve.trading.strategy.commons.arbitrage;

import java.time.Duration;
import java.util.HashMap;

/**
 * @param name
 * @param slaveQuoteVolume Размер квоты. Для CM указываем в долларах, для UM указывает в token-ах.
 * @param pfToOpen
 * @param pfToClose
 * @param deltaHalfLife
 */
public record InstrumentConfig(Pair name,
                               double slaveQuoteVolume,
                               double pfToOpen,
                               double pfToClose,
                               double extraPfToOpen,
                               boolean tradingInstrument,
                               Duration deltaHalfLife) {

    public static HashMap<Pair, InstrumentConfig> map = new HashMap<>();

    public static final Duration DELTA_HALF_LIFE = Duration.ofMinutes(2882);
    public static final double QUOTE_TIER1 = 4800;
    public static final double QUOTE_TIER2 = 2800;
    public static final double QUOTE_TIER3 = 1400; //квоты в usdt

    static {
        map.put(Pair.FIL, new InstrumentConfig(
                Pair.FIL,
                QUOTE_TIER1,
                0.0012,
                0.000,
                0.008,
                true,
                DELTA_HALF_LIFE));


    }
}

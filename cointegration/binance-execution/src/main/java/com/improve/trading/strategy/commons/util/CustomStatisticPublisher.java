package com.improve.trading.strategy.commons.util;

import com.improve.trading.api.strategy.Controls;
import com.improve.trading.strategy.commons.arbitrage.ArbitrageInstrument;
import com.improve.trading.strategy.service.IndexLogic;
import com.improve.trading.strategy.service.IndexParameters;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomStatisticPublisher extends DbStatisticPublisher {

    private final Map<String, Object> statisticRecords = new LinkedHashMap<>();

    public CustomStatisticPublisher(Controls controls, Duration statisticSaveInterval) {
        super(controls, statisticSaveInterval);
    }

    public void publishConfigs(Instant time, IndexLogic indexLogic) {
        IndexParameters params = indexLogic.params;
        statisticRecords.clear();
        statisticRecords.put("t", "I");
        statisticRecords.put("id", params.getId());
        statisticRecords.put("indexName", params.getIndexName());
        statisticRecords.put("quoteSize", params.getQuoteSize());
        statisticRecords.put("windowMA", params.getWindowMA());
        statisticRecords.put("useEWM", params.isUseEWM());
        statisticRecords.put("cEnter", params.getCEnter());
        statisticRecords.put("cExit", params.getCExit());
        statisticRecords.put("cStop", params.getCStop());
        statisticRecords.put("stdThreshold", params.getStdThreshold());
        statisticRecords.put("leads", String.join(",", params.getLeads()));
        statisticRecords.put("lags", String.join(",", params.getLags()));
        Map<String, Double> normPrices = indexLogic.normPrices;
        statisticRecords.put("normPrices", normPrices.keySet().stream()
                .map(key -> key + "=" + normPrices.get(key))
                .collect(Collectors.joining(", ", "{", "}")));
        Map<String, Double> weight = indexLogic.weight;
        statisticRecords.put("weight", weight.keySet().stream()
                .map(key -> key + "=" + weight.get(key))
                .collect(Collectors.joining(", ", "{", "}")));
        publish(time, statisticRecords);
    }

    public void publishSpreads(Instant time, Map<String, IndexLogic> combinationLogicMap) {
        for(Map.Entry<String, IndexLogic> combination : combinationLogicMap.entrySet()) {
            IndexLogic cl = combination.getValue();
            statisticRecords.clear();
            statisticRecords.put("t", "S");
            statisticRecords.put("id", cl.params.getId());
            statisticRecords.put("V", cl.spreadMA.lastValue);
            statisticRecords.put("E", cl.spreadMA.getEwm());
            statisticRecords.put("A", cl.spreadMA.getMA());
            statisticRecords.put("S", cl.spreadMA.getStd());
            statisticRecords.put("T", cl.targetPosition);
            statisticRecords.put("P", cl.positionUSD);
            publish(time, statisticRecords);
        }
    }

    public void publish(Instant time,
                        long start,
                        long prevStart,
                        String balanceCoin,
                        double totalLeverage,
                        Map<String, ArbitrageInstrument> instruments,
                        Map<String, IndexLogic> combinationLogicMap
    ) {
//        for (ArbitrageInstrument any : instruments.values()) {
//            statisticRecords.clear();
//            statisticRecords.put("time", time);
//            statisticRecords.put("passedFromPrevLaunch", start - prevStart);
//            statisticRecords.put("runTime", System.nanoTime() - start);
//            statisticRecords.put("slaveBalance", any.getSLaveBalance());
//            statisticRecords.put("totalBalance", any.getSLaveBalance());
//            statisticRecords.put("balanceCoin", balanceCoin);
//            statisticRecords.put("totalLeverage", totalLeverage);
//            break;
//        }

//        instruments.values().forEach(instrument -> statisticRecords.put("instrumentLeverage_" + instrument.getName(), instrument.getLeverage()));
//        instruments.values().forEach(instrument -> statisticRecords.put("instrumentPNL_" + instrument.getName(), instrument.getPnlCalculator().getPnl()));
//        instruments.values().forEach(instrument -> statisticRecords.put("instrumentFee_" + instrument.getName(), instrument.getPnlCalculator().getFee()));
//        publish(time, statisticRecords);
    }

}

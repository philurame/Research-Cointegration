package com.improve.trading.strategy.service;

import com.improve.trading.strategy.commons.arbitrage.ArbitrageInstrument;
import com.improve.trading.strategy.commons.arbitrage.Index;
import com.improve.trading.strategy.commons.arbitrage.Pair;
import com.improve.trading.strategy.commons.arbitrage.Signal;
import com.improve.trading.strategy.commons.util.BufferedLogger;

import java.util.HashMap;
import java.util.Map;

public class IndexRoutine {

    BufferedLogger blog;

    Map<Index, Double> mapTargetLead = new HashMap<>();
    Map<Index, Double> mapTargetAll = new HashMap<>();
    Map<Index, Double> mapTargetLag = new HashMap<>();
    Map<Index, Boolean> isStopTradingLead = new HashMap<>();
    Map<Index, Boolean> isStopTradingLag =  new HashMap<>();

    Map<Index, Double> mapLagPositionInUsd = new HashMap<>();
    Map<Index, Double> mapLeadPositionInUsd = new HashMap<>();
    Map<Index, Double> mapLeadPosition = new HashMap<>();
    Map<Index, Double> mapLeadPositionTemp = new HashMap<>();
    Map<Index, Double> mapLagPrice = new HashMap<>();
    Map<Index, Double> mapLeadPrice = new HashMap<>();
    Map<String, Double> indexTargets = new HashMap<>();
    public Map<Index, Double> indexPositionsUSD = new HashMap<>();


    Map<Index, Integer> instrumentNumbers;

//    boolean checkSpread = false;
    //todo
    boolean onlyClose = false;
    private boolean trading = true;

    public IndexRoutine(BufferedLogger blog, Map<Index, Integer> instrumentNumbers) {
        this.blog = blog;
        this.instrumentNumbers = instrumentNumbers;
    }

    private Signal getSignal(String action) {
        return switch (action) {
            case "wait" -> Signal.WAIT;
            case "open sell" -> Signal.SELL;
            case "open buy" -> Signal.BUY;
            case "close sell" -> Signal.CLOSE_BUY;
            case "close buy" -> Signal.CLOSE_SELL;
            case "stop sell" -> Signal.STOP_BUY;
            case "stop buy" -> Signal.STOP_SELL;
            default -> throw new IllegalArgumentException("Unsupported signal");
        };
    }

//    void onInstrument(ArbitrageInstrument arbitrageInstrument) {
//        List<Index> inds = arbitrageInstrument.index;
//
//        for (Index ind : inds) {
//            Map<Index, Double> tmpMap2 = ind.isMaster ? mapLagData : mapLeadData;
//            Double data = ind.isMaster ? arbitrageInstrument.getDataMaster() : arbitrageInstrument.getDataSlave();
//            Double val2 = tmpMap2.getOrDefault(ind, 0.0);
//            tmpMap2.put(ind, val2 + data);
//
//
//            // вот здесь проблема
//            Map<Index, Double> tmpMap = ind.isMaster ? mapLagPositionInUsd : mapLeadPositionInUsd;
//            Double pos = arbitrageInstrument.getSlavePositionInUsd();
//            Double val = tmpMap.getOrDefault(ind, 0.0);
//            tmpMap.put(ind, val + pos);
//
//        }
//    }


    void onInstruments(Map<String, ArbitrageInstrument> map) {


        mapLagPrice.clear();
        mapLeadPrice.clear();
        mapLagPositionInUsd.clear();
        mapLeadPositionInUsd.clear();
        indexTargets.clear();

        for (ArbitrageInstrument ar : map.values()) {

            for (Index ind : ar.index) {
                Map<Index, Double> tmpMap2 = ind.isLag ? mapLagPrice : mapLeadPrice;
                // todo переехать с мид праса потом
                Double data = ar.getSlavePrice();
                Double val2 = tmpMap2.getOrDefault(ind, 0.0);

                tmpMap2.put(ind, val2 + data);

                // вот здесь проблема
                Map<Index, Double> tmpMap = ind.isLag ? mapLagPositionInUsd : mapLeadPositionInUsd;
                Double pos = ar.getSlavePositionInUsd();
                Double val = tmpMap.getOrDefault(ind, 0.0);
                tmpMap.put(ind, val + pos);

            }
        }
    }

    void onIndex(String indexName, Index indexLead, Index indexLag, IndexLogic cl, double balanceUsd) {

        isStopTradingLead.put(indexLead, false);
        isStopTradingLag.put(indexLag, false);
//        Signal signal = cl.getSignal();

//        double minPrice = 0.0;
//        double xSize = Math.abs(cl.getTargetPosition());
//        double stop = 0.0;
//        double defaultSize = 0.1;
//        double slavePosition = mapLeadPosition.get(indexSlave);

        // вот здесь проблема
//        double slavePositionInUsd = mapLeadPositionInUsd.get(indexSlave);
//        double masterPositionInUsd = mapLagPositionInUsd.get(indexMaster);

        // вот так нельзя было даже раньше
        double targetLead = 0;//slavePositionInUsd;
        double targetLag = 0;//masterPositionInUsd;

//        if (signal == Signal.BUY || signal == Signal.SELL) {
//            xSize = Math.max(defaultSize, 0.1);
//        } else if ((signal == Signal.CLOSE_BUY) || (signal == Signal.STOP_BUY)) { // && (slavePositionInUsd > 0 || masterPositionInUsd < 0)) {
//            xSize = Math.max(defaultSize, 0.1);
//        }

//                    double realSpreadBuy = mapLeadData.get(indexSlave) / instrumentNumbers.get(indexSlave) - mapLagData.get(indexMaster) / instrumentNumbers.get(indexMaster);
//                    double realSpreadSell = mapLeadData.get(indexSlave) / instrumentNumbers.get(indexSlave) - mapLagData.get(indexMaster) / instrumentNumbers.get(indexMaster);
//

//        double realSpreadBuy = mapLeadPrice.get(indexSlave) / instrumentNumbers.get(indexSlave) - mapLagPrice.get(indexMaster) / instrumentNumbers.get(indexMaster);
//        double realSpreadSell = mapLagPrice.get(indexMaster) / instrumentNumbers.get(indexMaster) - mapLeadPrice.get(indexSlave) / instrumentNumbers.get(indexSlave);



//        realSpreadBuy = priceByVolAsk * combKf - priceByVolBid + combConst;
//        realSpreadSell = priceByVolBid * combKf - priceByVolAsk + combConst;
//
//        boolean isHedged = false;

//        if (tempPosSlave != mapLeadPosition.get(indexSlave)) {
//            isHedged = false;
//        }
//        mapLeadPositionTemp.put(indexSlave, slavePosition);
        // вот здесь проблема
        // todo как понять что все захэджено?
//        if ((Math.abs(slavePositionInUsd + masterPositionInUsd) / Math.abs(slavePositionInUsd)) < 0.05) {
//            isHedged = true;
//        }

        // Блок расчёта сайзов


        // todo логика когда позиция не пришла
        double currentPosition = indexPositionsUSD.getOrDefault(indexLead, 0.0);
        double targetPosition = cl.getTargetPosition(currentPosition / balanceUsd);
        targetLead = targetPosition * balanceUsd;
        targetLag = -targetPosition * balanceUsd;



//        if (signal == Signal.BUY) {
//            targetSlave = xSize * balanceUsd;
//            targetMaster = -xSize * balanceUsd;
//        } else if (signal == Signal.SELL) {
//            targetSlave = -xSize * balanceUsd;
//            targetMaster = xSize * balanceUsd;
//        } else if ((signal == Signal.CLOSE_BUY) || (signal == Signal.CLOSE_SELL)) {
//            targetSlave = 0.0;
//            targetMaster = 0.0;
//        } else if ((signal == Signal.STOP_BUY) || (signal == Signal.STOP_SELL)) {
//            targetSlave = 0.0;
//            targetMaster = 0.0;
//        }



//        if (onlyClose) {
//            targetMaster = Math.abs(targetMaster) > Math.abs(masterPositionInUsd) ? masterPositionInUsd : targetMaster;
//            targetSlave = Math.abs(targetSlave) > Math.abs(masterPositionInUsd) ? masterPositionInUsd : targetSlave;
//        }
//        if (Math.abs((Math.abs(masterPositionInUsd) - Math.abs(targetMaster))) / Math.abs(targetMaster) < 0.02 && Math.abs((Math.abs(slavePositionInUsd) - Math.abs(targetSlave))) / Math.abs(targetSlave) < 0.02 && masterPositionInUsd != 0 && slavePositionInUsd != 0) {
//            isStopTradingSlave.put(indexSlave, true);
//            isStopTradingMaster.put(indexMaster, true);
//        }
//        if (!DoubleUtil.isEqualZero(targetMaster) && Math.abs(targetMaster) / instrumentNumbers.get(indexMaster) < 10.5) {
//            if (targetMaster > 0) targetMaster = 11 * instrumentNumbers.get(indexMaster);
//            if (targetMaster < 0) targetMaster = - 11 * instrumentNumbers.get(indexMaster);
//            targetSlave = -targetMaster;
//        }

        mapTargetLead.put(indexLead, targetLead);
        mapTargetLag.put(indexLag, targetLag);
        mapTargetAll.put(indexLead, targetLead);
        mapTargetAll.put(indexLag, targetLag);

        blog.info("onIndex {} {}: | lag/lead: %.4f %.4f, balance %.4f",
                indexLead, indexLag,
                targetLag, targetLead, balanceUsd);

    }

    void onSignal(Map<String, ArbitrageInstrument> arbitrageInstruments) {
        // ======== Calculate other parameters
        for (ArbitrageInstrument arbitrageInstrument : arbitrageInstruments.values()) {
            double sumPos = 0.0;
            for (Index ind : arbitrageInstrument.index) {
                double pos = ind.isLag ? mapTargetLag.get(ind) / instrumentNumbers.get(ind) : mapTargetLead.get(ind) / instrumentNumbers.get(ind);
                blog.info("onSignal {}_{}: %.4f | lag/lead: %.4f %.4f",
                        arbitrageInstrument.getName(),
                        ind.name(),
                        pos,
                        mapTargetLag.get(ind),
                        mapTargetLead.get(ind));
                sumPos += pos;
            }

            arbitrageInstrument.onCalculate(sumPos, onlyClose);
        }
        // ======== Execute ========
        for (ArbitrageInstrument arbitrageInstrument : arbitrageInstruments.values()) {
            arbitrageInstrument.onExecute(trading);
        }

    }



    void decomposeIndex(Map<String, ArbitrageInstrument> map) {

        indexPositionsUSD.clear();

        for (Index idx : mapTargetLead.keySet()) {
            indexPositionsUSD.put(idx, 0.0);
        }

        for (ArbitrageInstrument ar : map.values()) {

            double sumPositiveFlow = 0;
            double sumNegativeFlow = 0;
            double undistributedPosition = ar.getSlavePositionInUsd();
            // ar.getTargetSlave() -
            for (Index ind : ar.index) {
                double indexPosition = indexPositionsUSD.getOrDefault(ind, 0.0);
                double w = mapTargetAll.get(ind) / instrumentNumbers.get(ind);
                if ((w > 0) && (undistributedPosition > 0)) {
                    w = Math.min(w, undistributedPosition);
                    undistributedPosition -= w;
                    sumPositiveFlow += w;
                    indexPositionsUSD.put(ind, indexPosition + w);
                } else if ((w < 0) && (undistributedPosition < 0)) {
                    w = Math.max(w, undistributedPosition);
                    undistributedPosition -= w;
                    sumNegativeFlow += w;
                    indexPositionsUSD.put(ind, indexPosition + w);
                }
            }
        }
    }

}

package com.improve.trading.strategy.service;

import com.improve.trading.api.common.Direction;
import com.improve.trading.api.strategy.Controls;
import com.improve.trading.api.strategy.data.market.orderbook.OrderBook;
import com.improve.trading.strategy.commons.util.DoubleUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FeesCalculate {

    private double prevPos;
    private boolean firstRun = true;

    public double totalVolume;
    public double totalVolumeBuy;
    public double totalVolumeSell;

    public double avgPFBuy;
    public double avgEntryBuy;
    public double avgSmthdDeltaBuy;

    public double avgPFSell;
    public double avgEntrySell;
    public double avgSmthdDeltaSell;
    public String name;

    FeesCalculate(String name) {
        this.name = name;
        this.prevPos = 0.0;
    }

    public void calculate(double newPos, double entryPrice, double currentPF, double smthPF, Controls controls, OrderBook orderbook) {
        if (firstRun) {
            prevPos = newPos;
            firstRun = false;
            publishMetrics(controls);
            return;
        }
//        if (newPos.abs().compareTo(prevPos.abs()) <= 0) {
//            prevPos = newPos;
//            publishMetrics(controls);
//            return;
//        }
        if (DoubleUtil.isEqual(newPos, prevPos)) return;

        boolean plusVol = newPos > prevPos;
        Direction dir = plusVol ? Direction.BUY : Direction.SELL;
        double deltaPos = Math.abs(newPos - prevPos);
        double negateKf = dir == Direction.BUY ? 1.0 : -1.0;
        double prevTotalVolume = totalVolume;
        totalVolume += deltaPos;
        totalVolumeBuy += plusVol ? deltaPos : 0.0;
        totalVolumeSell += !plusVol ? deltaPos : 0.0;

        boolean isTotalVolumeZeroBuy = DoubleUtil.isEqual(totalVolumeBuy, 0.0);
        boolean isTotalVolumeZeroSell = DoubleUtil.isEqual(totalVolumeSell, 0.0);
        double prevVolKfBuy = isTotalVolumeZeroBuy ? 0.0 : prevTotalVolume / totalVolumeBuy;
        double deltaKfBuy = isTotalVolumeZeroBuy ? 1.0 : deltaPos / totalVolumeBuy;
        double prevVolKfSell = isTotalVolumeZeroSell ? 0.0 : prevTotalVolume / totalVolumeSell;
        double deltaKfSell = isTotalVolumeZeroSell ? 1.0 : deltaPos / totalVolumeSell;
        if (dir == Direction.BUY) {
            avgPFBuy = prevVolKfBuy * avgPFBuy + deltaKfBuy * currentPF;
            avgEntryBuy = prevVolKfBuy * avgEntryBuy + deltaKfBuy * entryPrice;
            avgSmthdDeltaBuy = prevVolKfBuy * avgSmthdDeltaBuy + deltaKfBuy * (currentPF - smthPF) * negateKf;
        } else {
            avgPFSell = prevVolKfSell * avgPFSell + deltaKfSell * currentPF;
            avgEntrySell = prevVolKfSell * avgEntrySell + deltaKfSell * entryPrice;
            avgPFSell = prevVolKfSell * avgSmthdDeltaSell + deltaKfSell * (currentPF - smthPF) * negateKf;
        }


        prevPos = newPos;
        double pf;
        if (dir == Direction.SELL) {
            pf = currentPF - smthPF;
        } else {
            pf = smthPF - currentPF;
        }

        log.info("CheckPF {}. deltaPos: {}, Direction: {}, smth: {}, pf: {}, masterBid: {}, masterAsk: {}", name, deltaPos, dir, smthPF, pf, orderbook.minAsk(), orderbook.maxBid());
        publishMetrics(controls);

    }

    private void publishMetrics(Controls controls) {
        controls.publish(name + " 1 avgPFBuy", avgPFBuy);
        controls.publish(name + " 2 avgEntryBuy", avgEntryBuy);
        controls.publish(name + " 3 avgSmthdDeltaBuy", avgSmthdDeltaBuy);
        controls.publish(name + " 4 totalVolumeBuy", totalVolumeBuy);
        controls.publish(name + " 5 avgPFSell", avgPFSell);
        controls.publish(name + " 6 avgEntrySell", avgPFBuy);
        controls.publish(name + " 7 avgPFBuy", avgPFBuy);
        controls.publish(name + " 8 totalVolumeSell", totalVolumeSell);
        controls.publish(name + " 9 totalVolume", totalVolume);
    }
}
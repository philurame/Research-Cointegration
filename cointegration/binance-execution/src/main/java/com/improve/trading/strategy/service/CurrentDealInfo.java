package com.improve.trading.strategy.service;

import com.improve.trading.strategy.commons.arbitrage.Index;
import lombok.Getter;
import lombok.Setter;

import java.util.Locale;

public class CurrentDealInfo {

    String indexName;
    Index masterIndex;
    Index slaveIndex;

    @Getter
    double openPrice;
    @Getter
    double executionPrice;
    @Getter
    double executionVolume;
    @Getter
    double takeProfitSpread;
    @Getter
    double stopLossSpread;
    @Getter
    boolean isBuy;
    @Getter
    @Setter
    boolean triggeredStop;
    @Getter
    @Setter
    boolean triggeredTake;

    public CurrentDealInfo(String indexName, double openPrice, double targetPosition, double takeProfitSpread, double stopLossSpread) {
        this.indexName = indexName;
        this.masterIndex = IndexLogic.getIndexByString(indexName, true);
        this.slaveIndex = IndexLogic.getIndexByString(indexName, false);
        this.openPrice = openPrice;
        executionPrice = openPrice;
        executionVolume = 0;

        isBuy = (targetPosition > 0);
        this.takeProfitSpread = takeProfitSpread;
        this.stopLossSpread = stopLossSpread;
        this.triggeredStop = false;
        this.triggeredTake = false;
    }

    public String toString(){
        return String.format(Locale.US, "openPrice=%.4f isBuy=%s, take/stop=%.4f / %.4f", openPrice, isBuy, takeProfitSpread, stopLossSpread);
    }
}

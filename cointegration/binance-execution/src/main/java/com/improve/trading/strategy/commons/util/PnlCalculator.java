package com.improve.trading.strategy.commons.util;

import com.improve.trading.api.strategy.Context;
import com.improve.trading.api.strategy.data.account.Execution;
import com.improve.trading.api.strategy.data.id.AccountId;
import com.improve.trading.api.strategy.data.id.TradingPairId;
import com.improve.trading.strategy.framework.api.context.AccountContextListener;
import lombok.Getter;

import java.time.Instant;
import java.util.Collection;

/**
 * Класс для вычисления PNL по Execution-ам.
 */
public class PnlCalculator implements AccountContextListener {


    TradingPairId slaveTradingPairId;
    @Getter
    double pnl = 0.0;
    @Getter
    double fee = 0.0;
    @Getter
    double position;
    double tmpPnl = 0.0;
    double tmpFee = 0.0;

    public PnlCalculator(TradingPairId slaveTradingPairId) {

        this.slaveTradingPairId = slaveTradingPairId;
    }

    private void process(Collection<Execution> executions) {
        if (executions != null && !executions.isEmpty()) {
            for (Execution execution : executions) {
                TradingPairId tradingPairId = execution.getTradingPairId();
                if (tradingPairId.equals(slaveTradingPairId)) {
                    process(execution);
                }
            }
        }
    }

    private void process(Execution execution) {
        tmpPnl -= execution.getDirection().applySign(execution.getPrice() * execution.getSize());
        tmpFee += execution.getFee();
        position += execution.getDirection().applySign(execution.getSize());

        if (DoubleUtil.isEqualZero(position)) {
            pnl += tmpPnl;
            fee += tmpFee;
            tmpPnl = 0.0;
            tmpFee = 0.0;
        }
    }

    @Override
    public AccountId getAccountId() {
        return null;
    }

    @Override
    public void onEvent(Instant time, Context context) {
        context.getAccountsData().values().forEach(a -> process(a.getExecutions()));
    }
}

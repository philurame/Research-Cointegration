package com.improve.trading.strategy.commons.util;

import com.improve.trading.api.strategy.data.StrategyConfig;
import com.improve.trading.api.strategy.data.id.AccountId;
import com.improve.trading.strategy.framework.highlevel.StrategyUtil;

public final class TradingUtil {

    private TradingUtil() {
    }

    public static AccountId buildAccountId(StrategyConfig.PrimaryConfig config, final int accountIndex) {
        return StrategyUtil.accountId(config.getAccounts().get(accountIndex), null);
    }

}

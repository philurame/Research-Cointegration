package com.improve.trading.strategy.commons.account;

import com.improve.trading.api.strategy.data.account.AccountData;
import com.improve.trading.api.strategy.data.account.Asset;
import com.improve.trading.strategy.framework.highlevel.StrategyUtil;

public class AccountUtil {

    public static final String BINANCE_BNB_ID = "BNB";

    public static Double getBNBBalance(AccountData accountData) {
        Asset asset = accountData.getAssets().get(BINANCE_BNB_ID);
        return asset == null ?
                0.0 :
                asset.getBalance();
    }

    public static Double getBTCXbtBalance(AccountData accountData) {
        return getXbtAsset(accountData).getBalance() * StrategyUtil.SATOSHI;
    }

    public static Asset getXbtAsset(AccountData accountData) {
        Asset xBt = accountData.getAssets().get("XBt");
        if (xBt == null) {
            xBt = accountData.getAssets().get("btc");
        }
        return xBt;
    }
}

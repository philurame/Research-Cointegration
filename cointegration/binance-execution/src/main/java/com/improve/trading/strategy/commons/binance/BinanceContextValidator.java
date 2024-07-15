package com.improve.trading.strategy.commons.binance;

import com.improve.trading.api.strategy.data.id.TradingPairId;
import com.improve.trading.strategy.commons.util.StrategySettings;
import com.improve.trading.strategy.framework.api.context.ContextValidator;
import com.improve.trading.strategy.framework.api.context.MarketContext;
import com.improve.trading.strategy.framework.api.validation.ValidationResult;
import com.improve.trading.strategy.framework.api.validation.Validator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BinanceContextValidator implements ContextValidator {

    private final Validator validator;

    public BinanceContextValidator(StrategySettings settings, TradingPairId tradingPairId) {
        validator = Validator.allMatch(new Validator[]{
                Validator.accountDataConnected(),
                Validator.marketDataConnected(),
                Validator.positionValid(tradingPairId),
                Validator.askAndBid(),
                Validator.orderBookUpToDate(settings.getBinanceOrderBookTimeout()),
//                Validator.fundingValidate()
        });
    }

    @Override
    public ValidationResult validate(MarketContext context) {
        return validator.apply(context);
    }
}

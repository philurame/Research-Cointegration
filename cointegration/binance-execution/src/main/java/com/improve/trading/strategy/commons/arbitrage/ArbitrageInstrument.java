package com.improve.trading.strategy.commons.arbitrage;

import com.improve.trading.api.common.ManualClosedCorrection;
import com.improve.trading.api.common.Order;
import com.improve.trading.api.common.OrderCorrection;
import com.improve.trading.api.strategy.Context;
import com.improve.trading.api.strategy.Controls;
import com.improve.trading.api.strategy.OrderControl;
import com.improve.trading.api.strategy.data.StrategyConfig;
import com.improve.trading.api.strategy.data.account.Asset;
import com.improve.trading.api.strategy.data.account.Execution;
import com.improve.trading.api.strategy.data.id.TradingPairId;
import com.improve.trading.api.strategy.data.market.orderbook.OrderBook;
import com.improve.trading.api.strategy.data.market.orderbook.levels.DoubleLevels;
import com.improve.trading.api.tracking.Tags;
import com.improve.trading.common.data.Exchange;
import com.improve.trading.common.tracking.micrometer.MicrometerUtil;
import com.improve.trading.strategy.commons.account.Participant;
import com.improve.trading.strategy.commons.binance.BinanceContextValidator;
import com.improve.trading.strategy.commons.correctors.PositionCorrectorType;
import com.improve.trading.strategy.commons.correctors.SpotPositionCorrector2;
import com.improve.trading.strategy.commons.correctors.orderbook.OrderBookAndTickerMerger;
import com.improve.trading.strategy.commons.correctors.orderbook.OrderBookCorrector2;
import com.improve.trading.strategy.commons.util.*;
import com.improve.trading.strategy.commons.util.ratelimiter.RateLimiter;
import com.improve.trading.strategy.commons.util.status.StatusHandler;
import com.improve.trading.strategy.framework.api.Notifier;
import com.improve.trading.strategy.framework.api.context.*;
import com.improve.trading.strategy.framework.api.manager.Decision;
import com.improve.trading.strategy.framework.api.manager.OrderManager;
import com.improve.trading.strategy.framework.api.validation.ValidationResult;
import com.improve.trading.strategy.framework.highlevel.NonFatalException;
import com.improve.trading.strategy.framework.impl.common.MarketPositionCorrector;
import com.improve.trading.strategy.framework.impl.common.PositionCalculator;
import com.improve.trading.strategy.framework.impl.common.ProcessorTools;
import com.improve.trading.strategy.framework.impl.common.TradeVolumeCalculator2;
import com.improve.trading.strategy.framework.impl.general.OrderManagerSettings;
import com.improve.trading.strategy.framework.impl.general.OrderManagerWithUpdate;
import com.improve.trading.strategy.framework.service.notifier.TelegramSender;
import com.improve.trading.strategy.framework.settings.PriceSizeSettings;
import com.improve.trading.strategy.framework.util.OrderUtils;
import com.improve.trading.strategy.service.IndexLogic;
import com.improve.trading.strategy.service.StrategyEventProcessor;
import io.micrometer.core.instrument.Timer;
import it.unimi.dsi.fastutil.doubles.Double2DoubleMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ArbitrageInstrument {

    public static final String CONSUL_BEST_ASK_BID_VOL = "bestAskBidVol";
    public static final String CONSUL_BEST_ASK_BID_VOL_SLOW = "bestAskBidVolSlow";
    public static final String CONSUL_DELTA = "delta";
    private static final BufferedLogger blog = BufferedLogger.getLogger();
    private static final long strategyStart = System.currentTimeMillis() + 15_000;

    private final StrategySettings settings;
    public ArrayList<Index> index;

    private final Instrument instrument;
    @Getter
    private final String name;
    private final Controls controls;
    private final RateLimiter slaveRateLimiter;
    private final Participant participant;
    private final Fee slaveFee;
    @Getter
    private final TradeVolumeCalculator2 slaveTradeVolume;

    private static final Map<TradingPairId, PriceSizeSettings> localPriceSizeSettings = new HashMap<TradingPairId, PriceSizeSettings>();

    private final MarketContextSupplier slaveContextSupplier;

    private final ContextValidator slaveContextValidator;

    private final OrderManager slaveOrderManager;

    private final PriceSizeSettings slavePriceSizeSettings;

    //private final ConsulMultiStorage<Double> consulStorage;

    private final StrategyRunningState2 instrumentRunningState;
    private final Pugalka pugalka;


    @Getter
    private final PnlCalculator pnlCalculator;
    private final HighPassFilter highPassFilter;

    private final int secondsToAdd;

    public MarketContext slaveContext;

    @Getter
    private double slavePosition;

    @Getter
    private double slavePositionInUsd;
    private double sumPosition;

    /**
     * Максимально разрешенная позиция. В тех же единицах, как и размер позиции.
     */
    private double maxPosSlave;
    /**
     * Максимальная поза, при превышении которой начнется автоматический сброс.  В тех же единицах, как и размер позиции.
     */
    private double maxPosSlaveReduce;


    private Instant time;
    private double currentPFRAW;
    private double currentPFRAWBuyWithFee;
    private double currentPFRAWSellWithFee;
    @Getter
    private double targetSlave;

    private Instant lastOpenOrderCommand = Instant.EPOCH;
    private Instant nextCheckTime = Instant.EPOCH;

    private PassiveExecution passiveExecution = null;

    @Getter
    private double leverage;
    private double minBalanceInUsd;
    private boolean isValid = true;
    private boolean isHedged;
    @Getter
    private double tempPosSlave;

    private Signal signal;
    private String pairs;

    @Getter
    private double slavePrice = 0;

    private double combKf;
    private double combConst;

    private boolean isStopTrading = false;
    private boolean onlyCloseThis = false;
    public ArrayList<IndexLogic> indexLogic;

    private Instant lastOpenOrderCommandSlave = Instant.EPOCH;

    public void addIndex(Index idx, IndexLogic cl) {
        index.add(idx);
        if ("BINANCE".equals(instrument.slavePair().getExchangeId())) {
            cl.updateSpotStatus(instrument.slavePair().getTradingPairId());
        }

        indexLogic.add(cl);
    }
    public static void addPSS(Exchange exchange, String pairId, double priceStep, double sizeStep, double contractSize) {
        localPriceSizeSettings.put(new TradingPairId(exchange.toString(), pairId), new PriceSizeSettings(priceStep, sizeStep, contractSize));
    }

    static {
        addPSS(Exchange.BINANCE, "BLUEBIRDUSDT", 0.001D, 0.1D, 1.0D);
    }

    public ArbitrageInstrument(
            StrategyConfig.PrimaryConfig config,
            StrategySettings settings,
            Instrument instrument,
            ContextEventHandler contextEventHandler,
            Clock clock,
            Controls controls,
            RateLimiter slaveRateLimiter,
            Notifier notifier,
            TelegramSender telegramSender,
            StatusHandler statusHandler,
            int secondsToAdd,
            double combKf,
            double combConst,
            boolean onlyCloseThis,
            boolean isFirtsInstrument,
            boolean usePassive) {
        this.settings = settings;
        this.instrument = instrument;
        //this.name = instrument.config1().name().name();
        this.controls = controls;
        this.slaveRateLimiter = slaveRateLimiter;
        this.participant = Participant.create(config, instrument.slavePair(), instrument.accountId());
        this.index = new ArrayList<>();
        this.indexLogic = new ArrayList<>();


        // Exchange exchange;
        //    TradingPairId instrumentId;
        //    AccountId accountId;
        //    DataFeedId rateFeedId;
        //    StrategyConfig.TradingPairConfig tradingPairConfig;
        log.info("participant: {} {} {} {}", this.participant.getExchange(),
                this.participant.getInstrumentId(),
                this.participant.getAccountId(),
                this.participant.getTradingPairConfig().getTradingPairId());

        this.pairs = instrument.slavePair().getTradingPairId();
        this.name = pairs;
        this.combKf = combKf;
        this.combConst = combConst;

        this.secondsToAdd = secondsToAdd;

        slaveFee = Fee.get(participant.getExchange()); // todo

        // Trade Volume Calculators

        slaveTradeVolume = new TradeVolumeCalculator2(instrument.accountId(), participant.getInstrumentId());
        contextEventHandler.addListeners(slaveTradeVolume);
        Timer slaveTickToCommandTimer = MicrometerUtil.createTimer("strategy_tick_to_command", Tags.builder().add("exchange", participant.getExchange().name()).build());

        // ================================ SLAVE =================================
        ProcessorTools tools1 = createTools(contextEventHandler, controls, participant, settings, slaveRateLimiter, notifier, slaveTradeVolume, slaveTickToCommandTimer);
        slaveContextSupplier = tools1.getSupplier();
        slaveContextValidator = tools1.getValidator();
        slaveOrderManager = tools1.getOrderManager();
        slavePriceSizeSettings = tools1.getPriceSizeSettings();

        // ==========================================================================

        //this.consulStorage = ConsulStorageFactory.createMultiStorage(controls, Duration.ofMinutes(1), String.format("napoleon-mi-%s-%s", master.getInstrumentId(), slave.getInstrumentId()), Double.class);

        instrumentRunningState = new StrategyRunningState2(controls, telegramSender, "napoleon mi " + pairs);
        statusHandler.addSupplier(instrumentRunningState);

        this.pugalka = new Pugalka(name, 0.05, 0.5, 5, settings, 30, 30, 100);

        pnlCalculator = new PnlCalculator(instrument.slavePair());
        contextEventHandler.addListeners(pnlCalculator);

        highPassFilter = new HighPassFilter(0.5);



        // создаем FishingManager если нужно
        if (usePassive) {
            passiveExecution = new PassiveExecution(controls,
                    participant.getAccountId(),
                    instrument.slavePair(),
                    slaveRateLimiter,
                    notifier,
                    tools1.getOpenOrderListener(),
                    slavePriceSizeSettings);

            contextEventHandler.addListeners(passiveExecution.slaveFishingManager);
        }

        this.onlyCloseThis = onlyCloseThis;
    }

    public void onStart(Context context) {
        try {

            slaveContext = slaveContextSupplier.get();

            time = slaveContext.getTime();

            ValidationResult validationResult = slaveContextValidator.validate(slaveContext);
            if (!validationResult.isValid()) {
                String errors = validationResult.getErrorString();
                blog.info("{} Data is not valid. Strategy do not do anything.", name);
                blog.info("{} Validation result of slave: {}", name, errors);
                isValid = false;
                instrumentRunningState.stopped(time, "Slave validation errors: " + errors);
                return;
            }

            double avgPrice = slaveContext.getAvgRate();
            for (IndexLogic cl : indexLogic) {
                cl.updatePrice(instrument.slavePair().getTradingPairId(), avgPrice, time.toEpochMilli());
            }


            Collection<Execution> executions = slaveContext.getAccountData().getExecutions()
                    .stream()
                    .filter(c -> c.getTradingPairId().equals(instrument.slavePair()))
                    .collect(Collectors.toList());


            for (IndexLogic cl : indexLogic) {
                cl.updateExecutions(executions);
            }

            if (settings.isPortfolioMargin() && context.getAccountsData().get(participant.getAccountId()).getAccountInfo().getMarginAccountInfo() == null) {
                blog.info("{} MarginAccountInfo is null. Strategy do not do anything.", name);
                isValid = false;
                instrumentRunningState.stopped(time, "MarginAccountInfo is null.");
                return;
            }

            isValid = true;

            blog.info("{} slave {}", name, slaveContext.getPosition());

            logOrders(slaveContext);

            if (settings.isExtendedLogging()) {
                logOrderBooks(slaveContext);

                if (pairs.equals("AAVEUSDT_MATICUSDT")) {
                    blog.info("Balances in {}: slave: balance: {}, marginBalance: {}",
                            settings.getCoinName(),
                            slaveContext.getBalance(),
                            slaveContext.getMarginBalance());
                }

                blog.info("{} Funding. slave funding: {}, slave funding indicative: {}",
                        name,

                        slaveContext.getFundingRate(), slaveContext.getFundingRateIndicative());

                blog.info("{} PNL USDT: total: %.4f, pnl: %.4f, fee: %.4f, pos: %.6f", name, pnlCalculator.getPnl() - pnlCalculator.getFee(), pnlCalculator.getPnl(), pnlCalculator.getFee(), pnlCalculator.getPosition());
            }

            // Блок расчёта средних


            // Блок расчёта позиций, балансов, плеч, хеджа

            double slaveBalanceInUsd;

            slavePositionInUsd = slaveContext.getPositionSize() * slaveContext.getAvgRate();
            slavePosition = slaveContext.getPositionSize();

            StrategyEventProcessor.balance = slaveContext.getMarginBalance();

            double instrumentMidPrice = (slaveContext.getMinAsk() + slaveContext.getMaxBid()) / 2.0;
            slavePrice = instrumentMidPrice * combKf;


        } catch (NonFatalException e) {
            log.error(e.getMessage(), e);
            blog.error(e.getMessage());
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            blog.error(e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void onCalculate(final double targetInUsd,
                            final boolean onlyClose
    ) {

        targetSlave = targetInUsd / slaveContext.getAvgRate();
        targetSlave = Math.abs(Math.abs(targetInUsd) - Math.abs(slavePositionInUsd)) < 10.5 ? slavePosition : targetSlave;
        if (onlyCloseThis || onlyClose) {
            targetSlave = (Math.abs(targetSlave) > Math.abs(slavePosition) || Math.abs(Math.abs(targetInUsd) - -Math.abs(slavePositionInUsd)) < 10.5) ? slavePosition : targetSlave;
        }

        targetSlave = DoubleUtil.pullToScale(targetSlave, slavePriceSizeSettings.getSizeStep(), RoundingMode.DOWN);

        blog.info("onCalculate {}: usdPos [%.2f] targetSlave: [%.4f] ",
                instrument.slavePair().getTradingPairId(),
                targetInUsd,
                targetSlave);

    }

    public void onExecute(final boolean trading) {
        if (!isValid) {
            // todo Подумать как правильно реагировать в этом случае - может быть закрыть все ордера?
            blog.info("Instrument {} is invalid", name);
            return;
        }

        if (passiveExecution != null) {
            passiveExecution.onEvent(slaveContext, 0.002, slaveContext.getAvgRate());
        }


        // Блок торговли (Если превышено плечо - заходим в блок сброса позы)
        if (trading) {
            if (Math.abs(slavePosition * slaveContext.getAvgRate()) > 10000) {
                // если перебрали, то прежде чем че-то делать с мастером надо снизить позу до нормы
                final double target = (maxPosSlave * Math.signum(slavePosition));
                slaveOrderManager.execute(Decision.passive(target), slaveContext);
                blog.info("{} Reduce position due to high volume. On slave {} --> {}", name, slavePosition, target);
            } else {
                instrumentRunningState.running(time);


                if (!isStopTrading) {
                    if (slaveContext.getOpenTradeOrders().isEmpty() && time.isAfter(lastOpenOrderCommandSlave)) {
                        double slavePrice = targetSlave - slavePosition > 0 ?
                                DoubleUtil.pullToScale(slaveContext.getMinAsk() * 1.025, slavePriceSizeSettings.getPriceStep(), RoundingMode.UP) :
                                DoubleUtil.pullToScale(slaveContext.getMaxBid() * 0.975, slavePriceSizeSettings.getPriceStep(), RoundingMode.DOWN);

                        double delta = slavePosition - targetSlave;
                        double correctedTarget = targetSlave;
                        if (instrument.accountId().getExchangeId().equals(Exchange.BINANCE.name())) {
                            correctedTarget += delta * 0.000540 * 2;
                        }

                        if (Math.abs(delta) *  slavePrice > 70.0) {
                            Decision active = Decision.active(correctedTarget, slavePrice);

                            blog.info("{} slave active decision: {}", name, active);
                            slaveOrderManager.execute(active, slaveContext);
                            lastOpenOrderCommandSlave = time.plusSeconds(1);
                        }
                    }
                }
            }
        } else {
            instrumentRunningState.stopped(time, "Trading is stopped by constant trading[" + trading + "]");
        }
    }

    public void onPublish() {
        // Palantir Publish
        if (time.isAfter(nextCheckTime)) {
//            if (index.equals(Index.DEFI_SLAVE)) {
//                publishBalances();
//            }
            log.info(String.format(Locale.US, "pairs: %s, slavePrice: %f", pairs, slavePrice));
            String n = index.toString();
            controls.publishWithCategory("04. currSpreads ", String.format(Locale.US, "[ slavePrice = %.4f ] [ tgSlave = %.4f ] [ balUsd = %.2f ]", slavePrice, targetSlave, slaveContext.getMarginBalance()), n, 2);
            controls.publishWithCategory("00. targetSlave ", String.format(Locale.US, "%.4f", targetSlave), n, 2);


//            controls.publishWithCategory("01. flat ", String.format(Locale.US, "[ " + adviser.flat().action() + " ] vol = %.4f price = %.4f stop = %.4f", adviser.flat().size(), adviser.flat().price(), adviser.flat().stop()), n, 2);
//            controls.publishWithCategory("02. buy ", String.format(Locale.US, "[ " + adviser.buy().action() + " ] vol = %.4f price = %.4f stop = %.4f", adviser.buy().size(), adviser.buy().price(), adviser.buy().stop()), n, 2);
//            controls.publishWithCategory("03. sell ", String.format(Locale.US, "[ " + adviser.sell().action() + " ] vol = %.4f price = %.4f stop = %.4f", adviser.sell().size(), adviser.sell().price(), adviser.sell().stop()), n, 2);
            controls.publishWithCategory("04. currSpreads ", String.format(Locale.US, "[ slavePrice = %.4f ] [ tgSlave = %.4f ] [ balUsd = %.2f ]", slavePrice, targetSlave, slaveContext.getMarginBalance()), n, 2);
            controls.publishWithCategory("041. isStopTrading ", isStopTrading, n, 2);

            controls.publishWithCategory("05. slave " + name, String.format(Locale.US, "Pos = %.4f (%.2f USDT) target = %.2f", slaveContext.getPositionSize(), slaveContext.getPositionSize() * slaveContext.getAvgRate(), targetSlave * slaveContext.getAvgRate()), n, 2);

            controls.publishWithCategory("07. Fundings " + name, String.format(Locale.US, "%s = %+.4f%%", participant.getShortName(), slaveContext.getFundingRate() == null ? null : slaveContext.getFundingRate() * 100), n, 2);
            controls.publishWithCategory("08. Fundings Indicative" + name, String.format(Locale.US, "%s = %+.4f%%", participant.getShortName(), slaveContext.getFundingRateIndicative() == null ? null : slaveContext.getFundingRateIndicative() * 100), n, 2);
            controls.publishWithCategory("09. getNumAllowed", participant.getShortName() + " getNumAllowed = " + slaveRateLimiter.getNumAllowed(), n, 2);

            controls.publishWithCategory("11. OB latency s" + name, getOrderBookLatencyString(slaveContext), n, 2);

            controls.publishWithCategory("13. Trade latency s" + name, getTradeLatencyString(slaveContext), n, 2);

            controls.publishWithCategory("14. Volumes " + name, String.format(Locale.US, "%s = %.2f %s", participant.getShortName(), slaveTradeVolume.getVolume(), name, name), n, 2);
            Asset asset = slaveContext.getAccountData().getAssets().get(settings.getCoinName());
            controls.publishWithCategory("15. Test balances", String.format(Locale.US, "Bal = %.5f, Mar = %.5f, Avai = %.5f", asset.getBalance(), asset.getMarginBalance(), asset.getAvailable()), n, 2);

            controls.publishWithCategory("16. PNL USDT", String.format("tot: %.4f, pnl: %.4f, fee: %.4f", pnlCalculator.getPnl() - pnlCalculator.getFee(), pnlCalculator.getPnl(), pnlCalculator.getFee()), n, 2);

            controls.publishWithHealthStatusAndCategory("17. State " + name, instrumentRunningState.getStatusComment(), instrumentRunningState.getStatus(), n, 2);

            nextCheckTime = time.plusSeconds(this.secondsToAdd);
        }

        // Statistic publish
        // todo
//        if (this.statisticPublisher.isTimeToPublish(time)) {
//
//            double spreadBuy;
//            double spreadBuyActive;
//            double spreadSell;
//            double spreadSellActive;
//            if (slavePosition > settings.getQuoteVolume()) {
//                spreadBuy = (fairSpread - workOpenAdv);
//                spreadBuyActive = (fairSpread - workOpenActiveAdv);
//                spreadSell = (fairSpread + workCloseAdv);
//                spreadSellActive = (fairSpread + workCloseActiveAdv);
//            } else if (slavePosition < -settings.getQuoteVolume()) {
//                spreadBuy = (fairSpread - workCloseAdv);
//                spreadBuyActive = (fairSpread - workCloseActiveAdv);
//                spreadSell = (fairSpread + workOpenAdv);
//                spreadSellActive = (fairSpread + workOpenActiveAdv);
//            } else {
//                spreadBuy = (fairSpread - workOpenAdv);
//                spreadBuyActive = (fairSpread - workOpenActiveAdv);
//                spreadSell = (fairSpread + workOpenAdv);
//                spreadSellActive = (fairSpread + workOpenActiveAdv);
//            }
//
//
//            this.statisticPublisher.publish(
//                    time,
//                    start,
//                    prevStart,
//                    masterContext.getMarginBalance(),
//                    slaveContext.getMarginBalance(),
//                    totalBalance,
//                    settings.getCoinName(),
//                    fundingSlave,
//                    fundingMaster,
//                    fairSpread,
//                    currentPFRAW,
//                    currentPfSmth,
//                    workOpenAdv,
//                    workOpenActiveAdv,
//                    workCloseAdv,
//                    workCloseActiveAdv,
//                    spreadBuy,
//                    spreadBuyActive,
//                    spreadSell,
//                    spreadSellActive,
//                    slavePositionInUsd,
//                    masterPositionInUsd,
//                    sumPosition,
//                    toHedgeTokens
//            );
//        }
    }



//    private void initializeSmoothing(StrategySettings settings, Instant time) {
//        Double value;
//        if (settings.getBestAskBidVolInitValueJson() != null
//                && (value = ((Map<String, Double>) JsonHelper.toEntity(settings.getBestAskBidVolInitValueJson(), Map.class)).get(name)) != null) {
//            bestAskBidVolSmthd.update(value, time);
//            log.info("{} bestAskBidVolSmthd initialized from settings by {} at {}, json: {}", name, value, time, settings.getBestAskBidVolInitValueJson());
//        } else {
//            ConsulRecord<Double> obj = consulStorage.load(CONSUL_BEST_ASK_BID_VOL);
//            bestAskBidVolSmthd.update(obj.getValue(), obj.getTime());
//            log.info("{} bestAskBidVolSmthd initialized from consul by {} at {}", name, obj.getValue(), obj.getTime());
//        }
//
//        if (settings.getBestAskBidVolSlowInitValueJson() != null
//                && (value = ((Map<String, Double>) JsonHelper.toEntity(settings.getBestAskBidVolSlowInitValueJson(), Map.class)).get(name)) != null) {
//            bestAskBidVolSmthdSlow.update(value, time);
//            log.info("{} bestAskBidVolSmthdSlow initialized from settings by {} at {}, json: {}", name, value, time, settings.getBestAskBidVolSlowInitValueJson());
//        } else {
//            ConsulRecord<Double> obj = consulStorage.load(CONSUL_BEST_ASK_BID_VOL_SLOW);
//            bestAskBidVolSmthdSlow.update(obj.getValue(), obj.getTime());
//            log.info("{} bestAskBidVolSmthdSlow initialized from consul by {} at {}", name, obj.getValue(), obj.getTime());
//        }
//
//        if (settings.initialDeltasJson != null
//                && (value = ((Map<String, Double>) JsonHelper.toEntity(settings.initialDeltasJson, Map.class)).get(name)) != null) {
//            currentPfSmth.update(value, time);
//            log.info("{} Delta initialized from settings by {} at {}, json: {}", name, value, time, settings.initialDeltasJson);
//        } else {
//            ConsulRecord<Double> obj = consulStorage.load(CONSUL_DELTA);
//            currentPfSmth.update(obj.getValue(), obj.getTime());
//            log.info("{} Delta initialized from consul by {} at {}", name, obj.getValue(), obj.getTime());
//        }
//    }

    private void logOrders(MarketContext slaveContext) {
        logOrders(slaveContext, name + " slave");
        //slaveFishingManager.logDetailedOrders(name + " slave");
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

    private void logOrders(MarketContext context, String s) {
        if (context.getTradeOrders().size() > 0) {
            blog.info("{} orders: account: {}, instrument: {}, trade: {}, open-trade: {}", s,
                    context.getAccountOrders().size(), context.getOrders().size(),
                    context.getTradeOrders().size(), context.getOpenTradeOrders().size());
            context.getTradeOrders().forEach(order -> blog.info("  {}", order));
        }
        Collection<OrderCorrection> corrections = context.getCorrections();
        if (!corrections.isEmpty()) {
            blog.info(s + " corrections:");
            corrections.forEach(c -> blog.info(" {}", c));
        }
        Collection<ManualClosedCorrection> manualClosedCorrection = context.getManualClosedCorrection();
        if (!manualClosedCorrection.isEmpty()) {
            blog.info(s + " ManualClosedCorrection:");
            manualClosedCorrection.forEach(c -> blog.info(" {}", c));
        }
    }

    private void logOrderBooks(MarketContext slaveContext) {
        logOrderBook(slaveContext, name + " slave");
    }

    private void logOrderBook(MarketContext context, String label) {
        OrderBook orderBook = context.getOrderBook();
        blog.info("{} OrderBook: {}, lastUpdateSentAt: {}", label, orderBook, orderBook.getLastUpdateSentAt());
        blog.info("{} best bid: {}, ask: {}, ticker: {}", label, context.getMaxBid(), context.getMinAsk(), context.getTicker());
//        StringBuilder sb = new StringBuilder();
//        int i = 0;
//        for (Double2DoubleMap.Entry ask : orderBook.getAsks()) {
//            sb.insert(0, ask.getDoubleKey() + " -> " + ask.getDoubleValue() + "\n");
//
//            if (++i > 4) break;
//        }
//        sb.insert(0, "ask: \n");
//        sb.append("bids: \n");
//        i = 0;
//        for (Double2DoubleMap.Entry bid : orderBook.getBids()) {
//            sb.append(bid.getDoubleKey()).append(" -> ").append(bid.getDoubleValue()).append("\n");
//            if (++i > 4) break;
//        }
//        blog.info("{}", sb);
    }

    /**
     * Вычислить цену по которой нужны выставить ордер, чтобы забрать объем {@code volume}.
     *
     * @param levels             уровни ордербука
     * @param best               лучшая цена
     * @param volume             искомы объем.
     * @param discountFirstLevel пропускать ли первый уровень.
     * @param isBids             {@code true}, если считаем для bid-ов, {@code false} если для ask-ов.
     * @return цена ордера.
     */
    private double getPriceByVolume(DoubleLevels levels, double best, double volume, boolean discountFirstLevel, boolean isBids) {
        Iterator<Double2DoubleMap.Entry> iterator = levels.iterator();
        if (!iterator.hasNext()) return best;
        Double2DoubleMap.Entry entry = null;
        if (isBids) {
            //noinspection StatementWithEmptyBody
            while (iterator.hasNext() && (entry = iterator.next()).getDoubleKey() > best) {
            }
        } else {
            //noinspection StatementWithEmptyBody
            while (iterator.hasNext() && (entry = iterator.next()).getDoubleKey() < best) {
            }
        }

        if (discountFirstLevel /*&& entry.getDoubleValue() < bestAskBidVolSmthd.getDoubleCurrentValue()*/) {
            if (!iterator.hasNext()) return best;
            entry = iterator.next();
        }

        double tempPrice;
        do {
            double size = entry.getDoubleValue();
            if (size >= volume) {
                return entry.getDoubleKey();
            }
            volume -= size;
            tempPrice = entry.getDoubleKey();
            entry = iterator.hasNext() ? iterator.next() : null;
        } while (entry != null);
        return tempPrice;
    }

    /**
     * @return была ли какая-либо активносмть по ордерам.
     */
    public boolean hasOrderActivity() {
//        OrderControl masterControl = controls.orderControl(master.getAccountId(), master.getInstrumentId());
//        if (masterControl.getNumberOfCommands() > 0) return true;
        OrderControl slaveControl = controls.orderControl(participant.getAccountId(), participant.getInstrumentId());
        return slaveControl.getNumberOfCommands() > 0;
    }

    public boolean haveClosedOrdersOrCorrection() {
        return haveClosedOrdersOrCorrection(slaveContext);
    }

    private boolean haveClosedOrdersOrCorrection(MarketContext context) {
        if (context == null) return false;
        if (!context.getAccountData().getNewOrderCorrections().isEmpty()) return true;
        if (!context.getManualClosedCorrection().isEmpty()) return true;
        final Map<String, Order> accountOrders = context.getAccountOrders();
        if (accountOrders.isEmpty()) return false;
        return accountOrders.values().stream().anyMatch(OrderUtils.CLOSED_OR_ERROR);
    }

//    public double getMasterBalance() {
//        return masterContext.getMarginBalance();
//    }

    public double getSLaveBalance() {
        return slaveContext.getMarginBalance();
    }

    public void logExecutions() {
        Collection<Execution> executions = slaveContext.getAccountData().getExecutions();
        if (executions != null && !executions.isEmpty()) {
            blog.info("Executions collection on slave:");
            executions.forEach(e -> blog.info(" {}", e));
            blog.chatOnThisStep();
        }
    }

    private static Instant getNextFundingTime(Instant nextFundingTime, Instant now) {
        if (nextFundingTime.compareTo(now) >= 0) return nextFundingTime;
        final long prevFundingTime = nextFundingTime.getEpochSecond();
        final long delta = now.getEpochSecond() - prevFundingTime;
        final long n = (long) Math.floor((double) delta / 28800.0) + 1L;
        return Instant.ofEpochSecond(prevFundingTime + 28800 * n);
    }

    private static String getOrderBookLatencyString(MarketContext context) {
        return context.getLatencyOrderBookExchange() + "ms -> " + context.getLatencyOrderBookExchangeToEsr() + "ms -> " + context.getLatencyOrderBookEsrToStrategy() + "ms";
    }

    private static String getTradeLatencyString(MarketContext context) {
        return context.getLatencyTradeExchange() + "ms -> " + context.getLatencyTradeExchangeToEsr() + "ms -> " + context.getLatencyTradeEsrToStrategy() + "ms";
    }

    private static ProcessorTools createTools(ContextEventHandler contextEventHandler,
                                              Controls controls,
                                              Participant participant,
                                              StrategySettings settings,
                                              RateLimiter rateLimiter,
                                              Notifier notifier,
                                              OpenOrderListener tradeVolume,
                                              Timer tickToCommandTimer) {


        PriceSizeSettings priceSizeSettings = localPriceSizeSettings.getOrDefault(participant.getInstrumentId(), PriceSizeSettings.get(participant.getInstrumentId()));


        MarketContextSupplier contextSupplier = new MarketContextSupplier(participant.getAccountId(), participant.getInstrumentId());
        String spotAsset = participant.getInstrumentId().getTradingPairId().replace("USDT", "");
        PositionCalculator positionCalculator = createPositionCalculator(participant, spotAsset);
        ContextValidator contextValidator = createContextValidator(participant, settings);
        ContextBuilder contextBuilder = buildContextBuilder(participant, positionCalculator, controls, settings, priceSizeSettings);
        contextEventHandler.addHandler(contextBuilder);


        OpenOrderListener openOrderListener = (orderId, direction) -> {
            tradeVolume.openOrder(orderId, direction);
            positionCalculator.openOrder(orderId, direction);
        };

        OrderManager orderManager = switch (participant.getExchange()) {
            case BINANCE, BINANCE_MARGIN, BINANCE_FUTURES, BINANCE_DELIVERY -> {
                OrderManagerSettings orderManagerSettings = new OrderManagerSettings(
                        Duration.ofMillis(settings.getDelayToClose()),
                        true,
                        priceSizeSettings.getPriceStep(),
                        priceSizeSettings.getSizeStep());

                yield new OrderManagerWithUpdate(
                        participant.getAccountId(),
                        participant.getInstrumentId(),
                        controls,
                        orderManagerSettings,
                        rateLimiter,
                        notifier,
                        openOrderListener,
                        tickToCommandTimer
                );
            }
            case DERIBIT, GATE_FUTURES_BTC, GATE_FUTURES_USDT, GATE_FUTURES_USD -> {
                OrderManagerSettings managerSettings = new OrderManagerSettings(
                        Duration.ofSeconds(1),
                        true,
                        priceSizeSettings.getPriceStep(),
                        priceSizeSettings.getSizeStep());

                yield new OrderManagerWithUpdate(
                        participant.getAccountId(),
                        participant.getInstrumentId(),
                        controls,
                        managerSettings,
                        rateLimiter,
                        notifier,
                        openOrderListener,
                        tickToCommandTimer
                );
            }
            default -> throw new IllegalArgumentException("Unsupported order manager for " + participant.getExchange());
        };

        contextEventHandler.addListeners(orderManager, contextSupplier);
        return new ProcessorTools(contextSupplier, contextValidator, orderManager, priceSizeSettings, openOrderListener);
    }

    private static ContextBuilder buildContextBuilder(
            Participant participant,
            PositionCalculator masterPositionCalculator,
            Controls controls,
            StrategySettings settings,
            PriceSizeSettings priceSizeSettings) {
        ContextBuilder builder = MarketContextBuilderFactory.create(participant.getAccountId(), participant.getInstrumentId(), settings.getCoinName(), participant.getTradingPairConfig());
        if (participant.getExchange().equals(Exchange.BINANCE)) {
            builder = new OrderBookAndTickerMerger(builder, participant.getInstrumentId());
        } else {
            builder = new OrderBookCorrector2(builder, participant.getAccountId(), participant.getInstrumentId(), priceSizeSettings.getPriceStep(), blog, false, null);
        }

        builder = new OrderContextBuilder2(builder, participant.getAccountId(), participant.getInstrumentId(), controls.orderControl(participant.getAccountId(), participant.getInstrumentId()), priceSizeSettings, true, false);
        return new MarketPositionCorrector(builder, masterPositionCalculator);
    }

    private static PositionCalculator createPositionCalculator(Participant participant, String positionAssetId) {
        return switch (participant.getExchange()) {
            case DERIBIT, GATE_FUTURES_BTC, GATE_FUTURES_USD, GATE_FUTURES_USDT -> new PositionCalculator(participant.getInstrumentId(), PositionCorrectorType.BY_ORDERS_AND_SIZE);
            case BINANCE_FUTURES, BINANCE_DELIVERY, BINANCE_MARGIN -> new PositionCalculator(participant.getInstrumentId(), PositionCorrectorType.POSITION_CORRECTOR_5);
            case BINANCE -> new PositionCalculator(new SpotPositionCorrector2(participant.getInstrumentId(), positionAssetId, false));
            default -> throw new IllegalArgumentException("Unsupported exchange " + participant.getExchange() + " for position calculator");
        };
    }

    private static ContextValidator createContextValidator(Participant participant, StrategySettings settings) {
        return switch (participant.getExchange()) {
            case BINANCE, BINANCE_FUTURES, BINANCE_DELIVERY -> new BinanceContextValidator(settings, participant.getInstrumentId());
//            case DERIBIT -> new DeribitContextValidator(settings);
//            case GATE_FUTURES_BTC, GATE_FUTURES_USDT, GATE_FUTURES_USD -> new GateContextValidator(settings);
            default -> throw new IllegalArgumentException("Unsupported exchange " + participant.getExchange() + " for context validator");
        };
    }
}
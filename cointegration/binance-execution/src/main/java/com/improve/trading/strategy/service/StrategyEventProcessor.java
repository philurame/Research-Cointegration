package com.improve.trading.strategy.service;

import com.improve.trading.api.strategy.*;
import com.improve.trading.api.strategy.data.StrategyConfig;
import com.improve.trading.api.strategy.data.account.AccountData;
import com.improve.trading.api.strategy.data.id.AccountId;
import com.improve.trading.api.strategy.data.market.TradingPairMetadata;
import com.improve.trading.common.data.Exchange;
import com.improve.trading.common.tracking.micrometer.MicrometerUtil;
import com.improve.trading.execution.metrics.MicrometerInternalStrategyMetrics;
import com.improve.trading.strategy.commons.advisor.WarmupAdviserData;
import com.improve.trading.strategy.commons.arbitrage.*;
import com.improve.trading.strategy.commons.util.*;
import com.improve.trading.strategy.commons.util.health.HealthChecker;
import com.improve.trading.strategy.commons.util.ratelimiter.BinanceRateLimiter2;
import com.improve.trading.strategy.commons.util.ratelimiter.DeribitRateLimiter;
import com.improve.trading.strategy.commons.util.ratelimiter.GateRateLimiter;
import com.improve.trading.strategy.commons.util.ratelimiter.RateLimiter;
import com.improve.trading.strategy.commons.util.status.StatusHandler;
import com.improve.trading.strategy.framework.api.Notifier;
import com.improve.trading.strategy.framework.api.context.ContextEventHandler;
import com.improve.trading.strategy.framework.highlevel.NonFatalException;
import com.improve.trading.strategy.framework.service.notifier.NotifierImpl;
import com.improve.trading.strategy.framework.service.notifier.TelegramSender;
import com.improve.trading.strategy.service.advisers.RestStatArbitrageDto;
import io.micrometer.core.instrument.Timer;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class StrategyEventProcessor implements PrimaryEventHandler {

    private static final BufferedLogger blog = BufferedLogger.getLogger();

    private final StrategyConfig.PrimaryConfig config;
    private final StrategySettings settings;
    private final Clock clock;
    private final Controls controls;

    private final ContextEventHandler contextEventHandler;
    private final TelegramSender telegramSender;
    private final StatusHandler statusHandler;
    private final Timer strategyActiveTimer;
    private Map<String, ArbitrageInstrument> arbitrageInstruments;
    private final Collection<String> indexNames;
    public static final long MONTH_MS = 1000L * 60L * 60L * 24L * 30L;
    public static final String CATEGORY_BALANCES = "Balances";
    public static final String SPREAD_STATS = "SPREAD_STATS";
    private final int secondsToAdd = 10;

    private final CustomStatisticPublisher statisticPublisher;

    /**
     * Время старта стратегии.
     */
    private long start;
    /**
     * Время предыдущего старта стратегии.
     */
    private long prevStart = -1L;
    private final long strategyStart = System.currentTimeMillis() + 15_000;

    private IndexRoutine ir;


    private Instant nextCheckTime = Instant.ofEpochMilli(0); // переменная для сохранения последнего вывода в палантир (раз в 5 сек выводится в палантир)


    private final HealthChecker healthChecker; // хелсчек в палантир (выводит базовые показатели)

    /**
     * переменная, для работы в основном в дэбаге, чтобы страта не выставляла ордера
     */
    private boolean trading = true;
    /**
     * режим "только закрытие позиций". Задается в настройках
     */
    private boolean onlyClose;

    private int iMax = 1;

    private final StringBuilder sbLeverage = new StringBuilder();

    private Signal signal;
    private double maxPosSlave;
    private int j = 0;
    Map<Index, Integer> instrumentNumbers = new HashMap<>();
    public static double balance;
    boolean checkSpread = false;
    private double initBalance;
    private boolean isInitBalance = false;
    private final HashMap<String, RateLimiter> rateLimiters = new HashMap<>();
    private final HashMap<String, AccountId> accounts = new HashMap<>();
    private HashMap<String, IndexParameters> indexParams;
    Notifier notifier;

    public Map<String, IndexLogic> combinationLogicMap;
    boolean paramsPublished = false;
    String lastIndexName = "NONE";



     public static HashMap<String, IndexParameters> getParameters() {
         HashMap<String, IndexParameters> indexParams = new HashMap<>();
    //     indexParams.put("DeFi", new IndexParameters(0, "DeFi",
    //             0.20,
    //             20_000,
    //             true,
    //             3.5,
    //             0,
    //             10,
    //             0.003,
    //             new String[]{"ETHUSDT", "UNIUSDT", "KAVAUSDT"},
    //             new String[]{"SUSHIUSDT", "ALPHAUSDT", "AAVEUSDT", "BELUSDT"})
    //     );
    //     indexParams.put("Nft", new IndexParameters(1, "Nft",
    //             0.20,
    //             28_000,
    //             true,
    //             3.9,
    //             0,
    //             10,
    //             0.003,
    //             new String[]{"MANAUSDT", "SANDUSDT"},
    //             new String[]{"ENJUSDT", "CHZUSDT"})
    //     );
    //     indexParams.put("Math1", new IndexParameters(2, "Math1",
    //             0.20,
    //             20_000,
    //             true,
    //             3.9,
    //             0,
    //             10,
    //             0.003,
    //             new String[]{"SANDUSDT", "FTMUSDT", "AVAXUSDT"},
    //             new String[]{"SOLUSDT"})
    //     );
    //    indexParams.put("Math2", new IndexParameters(3, "Math2",
    //            0.20,
    //            20_000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"MANAUSDT", "LDOUSDT"},
    //            new String[]{"ETHUSDT"})
    //    );
        // indexParams.put("Math3", new IndexParameters("Math3",
        //         0.15,
        //         28000,
        //         true,
        //         3.9,
        //         0,
        //         10,

        //         new String[]{"ARBUSDT", "DEFIUSDT", "OPUSDT"},
        //         new String[]{"ETHUSDT"})
        // );
        // indexParams.put("Math4", new IndexParameters("Math4",
        //         0.15,
        //         28000,
        //         true,
        //         3.9,
        //         0,
        //         10,

        //         new String[]{"DEFIUSDT", "SANDUSDT", "COTIUSDT"},
        //         new String[]{"LTCUSDT"})
        // );
        // indexParams.put("Math5", new IndexParameters("Math5",
        //         0.15,
        //         28000,
        //         true,
        //         3.9,
        //         0,
        //         10,

        //         new String[]{"CHZUSDT", "ARPAUSDT"},
        //         new String[]{"MANAUSDT", "SANDUSDT", "BLUEBIRDUSDT"})
        // );
//        indexParams.put("Math6", new IndexParameters("Math6",
//                0.15,
//                20000,
//                true,
//                3.9,
//                0,
//                10,

//                new String[]{"MANAUSDT", "SANDUSDT", "ENJUSDT"},
//                new String[]{"DOTUSDT"})
//        );
//        indexParams.put("Math7", new IndexParameters("Math7",
//                0.15,
//                28000,
//                true,
//                3.9,
//                0,
//                10,

//                new String[]{"MATICUSDT", "MANAUSDT"},
//                new String[]{"DOTUSDT"})
//        );
        // indexParams.put("Math8", new IndexParameters("Math8",
        //         0.15,
        //         28000,
        //         true,
        //         3.9,
        //         0,
        //         10,

        //         new String[]{"DOGEUSDT", "BLUEBIRDUSDT"},
        //         new String[]{"MASKUSDT"})
        // );
        // indexParams.put("Math9", new IndexParameters("Math9",
        //         0.15,
        //         20000,
        //         true,
        //         3.9,
        //         0,
        //         10,

        //         new String[]{"ETHUSDT", "SXPUSDT"},
        //         new String[]{"DEFIUSDT", "ETCUSDT"})
        // );
//        indexParams.put("Math10", new IndexParameters("Math10",
//                0.15,
//                28000,
//                true,
//                3.9,
//                0,
//                10,

//                new String[]{"MATICUSDT", "ETCUSDT", "LINKUSDT"},
//                new String[]{"LTCUSDT"})
//        );
//        indexParams.put("Math11", new IndexParameters("Math11",
//                0.15,
//                28000,
//                true,
//                3.9,
//                0,
//                10,

//                new String[]{"MATICUSDT", "BTCUSDT", "BNBUSDT"},
//                new String[]{"LTCUSDT"})
//        );
//        indexParams.put("Math12", new IndexParameters("Math12",
//                0.15,
//                28000,
//                true,
//                3.9,
//                0,
//                10,

//                new String[]{"MATICUSDT", "MANAUSDT", "VETUSDT"},
//                new String[]{"DOTUSDT"})
    //    );
    //     indexParams.put("Math13", new IndexParameters("Math13",
    //             0.15,
    //             28000,
    //             true,
    //             3.9,
    //             0,
    //             10,

    //             new String[]{"DEFIUSDT", "MATICUSDT", "ENSUSDT"},
    //             new String[]{"SANDUSDT", "MANAUSDT", "ETCUSDT"})
    //     );
    //     indexParams.put("Math14", new IndexParameters("Math14",
    //             0.15,
    //             28000,
    //             true,
    //             3.9,
    //             0,
    //             10,

    //             new String[]{"ETHUSDT", "MATICUSDT", "IDEXUSDT"},
    //             new String[]{"SANDUSDT", "MANAUSDT", "ETCUSDT"})
    //     );
    //     indexParams.put("Math15", new IndexParameters("Math15",
    //             0.15,
    //             28000,
    //             true,
    //             3.9,
    //             0,
    //             10,

    //             new String[]{"BLUEBIRDUSDT", "MATICUSDT", "BELUSDT"},
    //             new String[]{"DEFIUSDT", "SANDUSDT", "MANAUSDT"})
    //     );
    //     indexParams.put("Math16", new IndexParameters("Math16",
    //             0.15,
    //             28000,
    //             true,
    //             3.9,
    //             0,
    //             10,

    //             new String[]{"BLUEBIRDUSDT", "ALICEUSDT"},
    //             new String[]{"SANDUSDT"})
    //     );
    //     indexParams.put("Math17", new IndexParameters("Math17",
    //             0.15,
    //             28000,
    //             true,
    //             3.9,
    //             0,
    //             10,

    //             new String[]{"BLUEBIRDUSDT", "MATICUSDT", "DEFIUSDT"},
    //             new String[]{"ETCUSDT", "MANAUSDT"})
    //     );
    //    indexParams.put("Math18", new IndexParameters(4, "Math18",
    //            0.20,
    //            20000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"UNIUSDT", "SUSHIUSDT"},
    //            new String[]{"COMPUSDT"})
    //    );

    //    indexParams.put("Math19", new IndexParameters(5, "Math19",
    //            0.20,
    //            20000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"SANDUSDT", "BANDUSDT"},
    //            new String[]{"COMPUSDT"})
    //    );


    //    indexParams.put("Math20", new IndexParameters(6, "Math20",
    //            0.20,
    //            28000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"ARBUSDT", "BCHUSDT"},
    //            new String[]{"ETHUSDT", "BTCUSDT"})
    //    );

    //    indexParams.put("Math21", new IndexParameters(7, "Math21",
    //            0.20,
    //            28000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"ETHUSDT", "BTCUSDT"},
    //            new String[]{"BCHUSDT", "ARBUSDT"})
    //    );

    //    indexParams.put("Math22", new IndexParameters(8, "Math22",
    //            0.20,
    //            20000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"COMPUSDT", "MATICUSDT", "ALPHAUSDT"},
    //            new String[]{"LTCUSDT"})
    //    );

    //    indexParams.put("Math23", new IndexParameters(9, "Math23",
    //            0.20,
    //            28000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"MATICUSDT", "FTMUSDT", "CFXUSDT"},
    //            new String[]{"MANAUSDT"})
    //    );

    //    indexParams.put("Math24", new IndexParameters(10, "Math24",
    //            0.20,
    //            28000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"MATICUSDT", "THETAUSDT"},
    //            new String[]{"MANAUSDT", "SANDUSDT"})
    //    );

    //    indexParams.put("Math25", new IndexParameters(11, "Math25",
    //            0.20,
    //            28000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"ETHUSDT", "MATICUSDT", "ARBUSDT"},
    //            new String[]{"OPUSDT"})
    //    );

    //    indexParams.put("Math26", new IndexParameters(12, "Math26",
    //            0.20,
    //            20000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"BTCUSDT", "ETHUSDT"},
    //            new String[]{"ARBUSDT", "ETCUSDT"})
    //    );

    //    indexParams.put("Math27", new IndexParameters(13, "Math27",
    //            0.20,
    //            28000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"MATICUSDT", "GRTUSDT"},
    //            new String[]{"ARBUSDT"})
    //    );

    //    indexParams.put("Math28", new IndexParameters(14, "Math28",
    //            0.20,
    //            20000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"DOTUSDT", "GMTUSDT"},
    //            new String[]{"SANDUSDT"})
    //    );

    //    indexParams.put("Math29", new IndexParameters(15, "Math29",
    //            0.20,
    //            28000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"SANDUSDT", "ADAUSDT"},
    //            new String[]{"DOTUSDT"})
    //    );

    //    indexParams.put("Math30", new IndexParameters(16, "Math30",
    //            0.20,
    //            28000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"MATICUSDT", "ATOMUSDT", "NEARUSDT"},
    //            new String[]{"DOTUSDT"})
    //    );

    //    indexParams.put("Math31", new IndexParameters(17, "Math31",
    //            0.20,
    //            5000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"ARBUSDT", "RENUSDT"},
    //            new String[]{"SANDUSDT", "MANAUSDT"})
    //    );

    //    indexParams.put("Math32", new IndexParameters(18, "Math32",
    //            0.20,
    //            20000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"XLMUSDT", "SANDUSDT", "XRPUSDT"},
    //            new String[]{"LTCUSDT"})
    //    );

    //    indexParams.put("Math33", new IndexParameters(19, "Math33",
    //            0.20,
    //            20000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"XRPUSDT", "SANDUSDT", "XLMUSDT"},
    //            new String[]{"LTCUSDT"})
    //    );

    //    indexParams.put("Math34", new IndexParameters(20, "Math34",
    //            0.20,
    //            28000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"AVAXUSDT", "SOLUSDT"},
    //            new String[]{"SANDUSDT", "MANAUSDT"})
    //    );

    //    indexParams.put("Math35", new IndexParameters(21, "Math35",
    //            0.20,
    //            28000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"DOTUSDT", "FILUSDT"},
    //            new String[]{"SANDUSDT"})
    //    );

    //    indexParams.put("Math36", new IndexParameters(22, "Math36",
    //            0.20,
    //            28000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"LTCUSDT", "HOOKUSDT"},
    //            new String[]{"SANDUSDT"})
    //    );

    //    indexParams.put("Math37", new IndexParameters(23, "Math37",
    //            0.20,
    //            20000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"SANDUSDT", "MATICUSDT", "IOSTUSDT"},
    //            new String[]{"LTCUSDT"})
    //    );

    //    indexParams.put("Math38", new IndexParameters(24, "Math38",
    //            0.20,
    //            28000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"MANAUSDT", "REEFUSDT"},
    //            new String[]{"CHZUSDT"})
    //    );

    //    indexParams.put("Math39", new IndexParameters(25, "Math39",
    //            0.20,
    //            20000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"SANDUSDT", "CHZUSDT", "GALAUSDT"},
    //            new String[]{"MASKUSDT"})
    //    );

    //    indexParams.put("Math40", new IndexParameters(26, "Math40",
    //            0.20,
    //            28000,
    //            true,
    //            3.9,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"DOTUSDT", "APEUSDT"},
    //            new String[]{"SANDUSDT", "MANAUSDT"})
    //    );


    //     indexParams.put("nMath0", new IndexParameters(27, "nMath0",
    //            0.10,
    //            28000,
    //            true,
    //            3.5,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"EOSUSDT"},
    //            new String[]{"BNBUSDT", "WAVESUSDT"})
    //    );

        indexParams.put("nMath1", new IndexParameters(28, "nMath1",
               0.10,
               28000,
               true,
               3.5,
               0,
               10,
               0.003,
               new String[]{"BLZUSDT"},
               new String[]{"ALGOUSDT", "IOTAUSDT", "SOLUSDT"})
       );

        indexParams.put("nMath2", new IndexParameters(29, "nMath2",
               0.10,
               28000,
               true,
               3.5,
               0,
               10,
               0.003,
               new String[]{"DOGEUSDT"},
               new String[]{"GRTUSDT"})
       );

        indexParams.put("nMath3", new IndexParameters(30, "nMath3",
               0.10,
               28000,
               true,
               4.2,
               0,
               10,
               0.003,
               new String[]{"THETAUSDT"},
               new String[]{"ALGOUSDT", "UNIUSDT"})
       );

        indexParams.put("nMath4", new IndexParameters(31, "nMath4",
               0.10,
               28000,
               true,
               3.5,
               0,
               10,
               0.003,
               new String[]{"NEOUSDT"},
               new String[]{"MATICUSDT"})
       );

        indexParams.put("nMath5", new IndexParameters(32, "nMath5",
               0.10,
               28000,
               true,
               3.5,
               0,
               10,
               0.003,
               new String[]{"DOGEUSDT"},
               new String[]{"TRBUSDT", "AXSUSDT", "ZRXUSDT"})
       );

        indexParams.put("nMath6", new IndexParameters(33, "nMath6",
               0.10,
               28000,
               true,
               3.5,
               0,
               10,
               0.003,
               new String[]{"SNXUSDT"},
               new String[]{"UNIUSDT", "NEOUSDT"})
       );

        indexParams.put("nMath7", new IndexParameters(34, "nMath7",
               0.10,
               28000,
               true,
               3.5,
               0,
               10,
               0.003,
               new String[]{"AXSUSDT"},
               new String[]{"BNBUSDT", "THETAUSDT", "EOSUSDT"})
       );

        indexParams.put("nMath8", new IndexParameters(35, "nMath8",
               0.10,
               28000,
               true,
               3.5,
               0,
               10,
               0.003,
               new String[]{"BLZUSDT"},
               new String[]{"FILUSDT", "RUNEUSDT"})
       );

    //     indexParams.put("nMath9", new IndexParameters(36, "nMath9",
    //            0.10,
    //            28000,
    //            true,
    //            3.5,
    //            0,
    //            10,
    //            0.003,
    //            new String[]{"ADAUSDT"},
    //            new String[]{"WAVESUSDT", "ENJUSDT", "SOLUSDT", "ALGOUSDT"})
    //    );





//        int cnt = 0;
//
////        Map<String, Integer> indexOrder = new HashMap<>();
//        for (String index : indexParams.keySet()) {
//            indexOrder.put(index, cnt);
//            cnt++;
//        }

        return indexParams;
    }

    @Builder
    public StrategyEventProcessor(
            PrimaryEnvironment env,
            StrategySettings settings
    ) {
        this.clock = env.getClock();
        contextEventHandler = new ContextEventHandler(clock);
        this.config = env.getConfig();
        this.settings = settings;
        this.controls = env.getControls();
        this.telegramSender = new TelegramSender(clock, controls);
        this.notifier = new NotifierImpl(telegramSender, settings.isNotifyAboutOrders());

        combinationLogicMap = new HashMap<>();
        indexParams = getParameters();


        for (String indexName : indexParams.keySet()) {
            combinationLogicMap.put(indexName, new IndexLogic(indexParams.get(indexName)));
            lastIndexName = indexName;
        }

        for (int i = 0;  i < config.getAccounts().size(); ++i) {
            AccountId account = TradingUtil.buildAccountId(config, i);
            rateLimiters.put(account.getExchangeId(), createRateLimiter(account, settings));
            accounts.put(account.getExchangeId(), account);
        }

        for (String exchange: rateLimiters.keySet()) {
            contextEventHandler.addListeners(rateLimiters.get(exchange));
        }

        this.onlyClose = settings.isOnlyClose();

        healthChecker = HealthChecker.builder()
                .controls(controls)
                .config(config)
                .exchangeConfig(new HealthChecker.ExchangeConfig(Exchange.valueOf("BINANCE_FUTURES"), settings.getCoinName(), settings.isPortfolioMargin()))
//                .exchangeConfig(new HealthChecker.ExchangeConfig(Exchange.valueOf(spotAccount.getExchangeId()), settings.getCoinName(), settings.isPortfolioMargin()))
                .build();
        contextEventHandler.addListeners(healthChecker);

        statusHandler = new StatusHandler(controls);
        statusHandler.addSupplier(healthChecker);

        strategyActiveTimer = MicrometerUtil.createTimerWithoutTags("strategy_active_time");

        arbitrageInstruments = new HashMap<>();
        buildInstruments();

        this.statisticPublisher = new CustomStatisticPublisher(controls, settings.getStatisticSaveInterval());
        this.indexNames = indexParams.keySet();

        this.ir = new IndexRoutine(blog, instrumentNumbers);
        log.info("Strategy initialized");
        log.info("settings: {}", settings);
    }

    @Override
    public void init(Set<TradingPairMetadata> tradingPairMetadata) {

    }

    @Override
    public void shutdown() {
    }

    /**
     * Обработчик команд из палантира.
     *
     * @param command команда.
     */
    @SneakyThrows
    @Override
    public void onOuterCommandEvent(String command) {
        command = command.replaceAll("\"", "");
        telegramSender.sendMessage("accept command: " + command);
        log.info("accept command: {}", command);

        if ("trading".equals(command)) { // включает торговлю
            trading = true;
        }

        if ("stoptrading".equals(command)) { // выключает торговлю
            trading = false;
        }

        if ("onlyclose".equals(command)) { // только закрытие поз
            onlyClose = true;
        }

        if ("onlyclosefalse".equals(command)) { // и открытие и закрытие
            onlyClose = false;
        }

        if (HealthChecker.COMMAND.equals(command)) { // сбросить параметры хелсчека (например при плановом выводе средств, чтобы страта не горела красным
            healthChecker.resetBalances();
        }
    }

    @Override
    public void onEvent(Context context) {
        Instant time = clock.instant();
        start = System.nanoTime();

        try {
            blog.clear();
            blog.info("Time advanced to {}", time);

            contextEventHandler.handle(context);


            // ==== Advisers ====
            Map<String, Object> inputFromAdvisers = context.getInputFromAdvisers();
            boolean isReady = combinationLogicMap.containsKey(lastIndexName) && combinationLogicMap.get(lastIndexName).isReady;
            if ((inputFromAdvisers != null) && !inputFromAdvisers.isEmpty()) {
                if (!isReady) {
                    WarmupAdviserData input2;
                    if ((input2 = (WarmupAdviserData) inputFromAdvisers.get("DataWarmupAdviser")) != null) {

                        // переходный костыль
                        WarmupAdviserData inputTransformed = new WarmupAdviserData();
                        for (String pair : input2.keySet()) {
                            inputTransformed.put(pair.replaceFirst("BINANCE::", "").replaceFirst("BINANCE_FUTURES::", ""), input2.get(pair));
                        }

                        for (String p : combinationLogicMap.keySet()) {
                            combinationLogicMap.get(p).warmup(inputTransformed);
                        }
                    }
                }
            }

            if (!isReady) {
                blog.info("no data from DataWarmupAdviser");
                return;
            }


            // ======== Calculate initial parameters
            sbLeverage.setLength(0);
            double totalLeverage = 0.0;
            for (ArbitrageInstrument arbitrageInstrument : arbitrageInstruments.values()) {
                arbitrageInstrument.onStart(context);
                sbLeverage.append(arbitrageInstrument.getName()).append(": ").append(arbitrageInstrument.getLeverage()).append(", ");
                totalLeverage += arbitrageInstrument.getLeverage();
            }
            blog.info("Total leverage: {}, {}", totalLeverage, sbLeverage.toString());

//            arbitrageInstruments.logExecutions();

            // todo убрать создание всех этих мапов на каждом тике
            // todo затереть дефолтные


            // вычисляем цены и позиции по всем индексам
            ir.onInstruments(arbitrageInstruments);
//            for (ArbitrageInstrument arbitrageInstrument : arbitrageInstruments.values()) {
//
//            }

//            Map<String, RestStatArbitrageDto.Pair> mapSignals = new HashMap<>();
//            if (statArbAdviser != null) {
//                for (String indexName : indexNames) {
//                    if (statArbAdviser.get(indexName) == null) blog.warn("index " + indexName + " not found!");
//                    mapSignals.put(indexName, statArbAdviser.get(indexName));
//                }
//            }

            // todo поправить вычисление
            // отдельный расчет макс позы по индексам/инструментам
            double umBalance = context.getAccountsData().get(accounts.get("BINANCE_FUTURES")).getAccountInfo().getTotalMarginBalance().doubleValue();
            AccountData spotAccountData = context.getAccountsData().get(accounts.get("BINANCE"));
            double spotBalance = spotAccountData.getAssets().get("USDT").getBalance();
            double totalBalance = umBalance + spotBalance;

            maxPosSlave = totalBalance * settings.getMaxLeverageForFullBalance();

            for (String indexName : indexNames) {
                IndexLogic cl = combinationLogicMap.get(indexName);
                Index indexLead = IndexLogic.getIndexByString(indexName, false);
                Index indexLag = IndexLogic.getIndexByString(indexName, true);

                ir.onIndex(indexName, indexLead, indexLag, cl, totalBalance);
            }

            ir.onSignal(arbitrageInstruments);

            ir.decomposeIndex(arbitrageInstruments);
            // ======== Publish ========
            for (ArbitrageInstrument arbitrageInstrument : arbitrageInstruments.values()) {
//                    Index ind = arbitrageInstrument.getIndex();

                arbitrageInstrument.onPublish();
            }


            if (!paramsPublished) {
                boolean canPublish = true;
                for (String a : combinationLogicMap.keySet()) {
                    canPublish &= combinationLogicMap.get(a).isReady;

                    blog.info("publishConfigs{}={}", a, combinationLogicMap.get(a).isReady);
                }

                blog.info("publishConfigs={}", canPublish);

                if (canPublish) {
                    for (String a : combinationLogicMap.keySet())
                        statisticPublisher.publishConfigs(Instant.now(), combinationLogicMap.get(a));

                    paramsPublished = true;
                }
            }
            //publishPalantir(time, ca, totalLeverage);
            publishStatistic(time, totalLeverage);
            if (time.isAfter(nextCheckTime)) {
                publishBalances(context);
                nextCheckTime = time.plusSeconds(this.secondsToAdd);
            }

        } catch (NonFatalException e) {
            log.error(e.getMessage(), e);
            blog.error(e.getMessage());
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            blog.error(e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            statusHandler.sendStatus();
            final boolean hasOrderActivity = hasOrderActivity();
            chatDelays(time, controls, hasOrderActivity);
            if ((hasOrderActivity || haveClosedOrdersOrCorrections()) && settings.isExtendedLogging()) {
                blog.chatOnThisStep();
            }
            blog.log(log, time);
            prevStart = start;
        }
    }

    private void publishStatistic(Instant time, double totalLeverage) {
        if (this.statisticPublisher.isTimeToPublish(time)) {
            this.statisticPublisher.publishSpreads(
                    time,
                    combinationLogicMap
            );
        }
    }

//    private void publishPalantir(Instant time, List<CalendarAdviser.VolatilityEvent> ca, double totalLeverage) {
//        if (time.isAfter(nextCheckTime)) {
//            boolean isActualNews = false;
//            if (ca != null) {
//                int i = 1;
//                Duration toNews = Duration.ofDays(1);
//                for (CalendarAdviser.VolatilityEvent ve : ca) {
//                    if (ve.timestamp().getTime() > time.toEpochMilli() && !isActualNews) {
//                        toNews = Duration.between(time, ve.timestamp().toInstant());
//                        String date = FORMAT_DATE.format(ve.timestamp());
//                        controls.publishWithCategory(i + ".", String.format(Locale.US, date + " [ %.0f ] [ %.0f ] [ %.0f ] pwr [ %.3f ] <<<<", ve.high(), ve.moderate(), ve.low(), ve.high() * 0.5 + ve.moderate() * 0.125 + ve.low() * 0.05), "News", i++);
//                        isActualNews = true;
//                    } else {
//                        String date = FORMAT_DATE.format(ve.timestamp());
//                        controls.publishWithCategory(i + ".", String.format(Locale.US, date + " [ %.0f ] [ %.0f ] [ %.0f ] pwr [ %.3f ]", ve.high(), ve.moderate(), ve.low(), ve.high() * 0.5 + ve.moderate() * 0.125 + ve.low() * 0.05), "News", i++);
//                    }
//                }
//                if (i > iMax) iMax = i;
//                while (iMax >= i) {
//                    controls.publishWithCategory(i + ".", "", "News", i++);
//                }
//                controls.publishWithCategory("000.", String.format(Locale.US, "Until the next news [ %d:%02d:%02ds ]", toNews.toHours(), toNews.toMinutesPart(), toNews.toSecondsPart()), "News", 3);
//            } else {
//                controls.publishWithCategory("000.", "Alarm! No data...", "News", 3);
//            }
//
//            for (ArbitrageInstrument arbitrageInstrument : arbitrageInstruments) {
//                controls.publishWithCategory(arbitrageInstrument.getName(), String.format("[Lvl = %.2f] [Gate = %.3f Binance = %.3f]", arbitrageInstrument.getLeverage(), arbitrageInstrument.getSlaveTradeVolume().getVolume(), arbitrageInstrument.getMasterTradeVolume().getVolume()), "Leverages and Volumes", 1);
//            }
//            controls.publishWithCategory("0. Total", String.format("%.2f", totalLeverage), "Leverages and Volumes", 1);
//
//            nextCheckTime = time.plusSeconds(5);
//        }
//    }

    /**
     * @return количество наносекунд прошедшее с момента старта стратегии до текущего момента.
     */
    private long getStrategyRunTime() {
        return System.nanoTime() - start;
    }



    private void chatDelays(Instant time, Controls controls, boolean hasOrderActivity) {
        if (time.toEpochMilli() < strategyStart) return;
        final long strategyRunTime = getStrategyRunTime();
        final long prevLaunchDelay = start - prevStart;
        blog.info("Strategy run time is {} ns, passed from previous launch: {} ns", strategyRunTime, prevLaunchDelay);
        StrategyMetrics metrics = controls.metrics();
        if (metrics instanceof MicrometerInternalStrategyMetrics m) {
            m.markStrategyTime(strategyRunTime);
            m.markStartDelayInterval(prevLaunchDelay);
            if (hasOrderActivity) strategyActiveTimer.record(strategyRunTime, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * @return была ли какая-либо активносмть по ордерам.
     */
    private boolean hasOrderActivity() {
        for (ArbitrageInstrument arbitrageInstrument : arbitrageInstruments.values()) {
            if (arbitrageInstrument.hasOrderActivity()) return true;
        }
        return false;
    }

    private boolean haveClosedOrdersOrCorrections() {
        for (ArbitrageInstrument arbitrageInstrument : arbitrageInstruments.values()) {
            if (arbitrageInstrument.haveClosedOrdersOrCorrection()) return true;
        }
        return false;
    }

    private static RateLimiter createRateLimiter(final AccountId accountId, StrategySettings settings) {
        Exchange exchange = Exchange.valueOf(accountId.getExchangeId());
        return switch (exchange) {
            case DERIBIT -> DeribitRateLimiter.create(accountId, DeribitRateLimiter.Tier.TIER2, true);
            case BINANCE, BINANCE_FUTURES, BINANCE_DELIVERY -> BinanceRateLimiter2.create(exchange, accountId, settings.getRateLimiterInitializeJson(), true);
            case GATE_FUTURES_BTC, GATE_FUTURES_USDT, GATE_FUTURES_USD -> GateRateLimiter.create(accountId, true);
            default -> throw new IllegalArgumentException("Unsupported exchange " + exchange + " for rate limiter");
        };
    }

    private void buildInstruments() {

        for (Map.Entry<String, IndexParameters> p : indexParams.entrySet()) {
            for (String l : p.getValue().getLeads()) {
                create(l, IndexLogic.getIndexByString(p.getKey(), false), 1.0, 0.0);
            }
            for (String l : p.getValue().getLags()) {
                create(l, IndexLogic.getIndexByString(p.getKey(), true), 1.0, 0.0);
            }
        }

    }

    private void create(String pair1, Index index, double combKf, double combConst) {
        j++;
        if (instrumentNumbers.containsKey(index)) {
            int val = instrumentNumbers.get(index);
            instrumentNumbers.put(index, val + 1);
        } else {
            instrumentNumbers.put(index, 1);
        }

        Instrument inst = Instrument.create(config, accounts, pair1);
        RateLimiter rl = rateLimiters.get(inst.accountId().getExchangeId());

        // на первом инструменте будем выставлять пассивно
        boolean usePassive = (j==1);//(pair1 == Pair.ETH);

        ArbitrageInstrument ar = arbitrageInstruments.getOrDefault(pair1, new ArbitrageInstrument(config, settings, inst,
                contextEventHandler, clock, controls, rl, notifier,
                telegramSender, statusHandler,
                secondsToAdd, combKf, combConst, false, j==1, usePassive));

        ar.addIndex(index, combinationLogicMap.get(IndexLogic.getIndexString(index)));

        this.arbitrageInstruments.putIfAbsent(pair1, ar);
    }

    private void publishSpreads(IndexLogic indexLogic, String ind) {
        controls.publishWithCategory("00. spread " + ind, String.format(Locale.US, "%.4f MA %.4f EWM %.4f STD  %.4f| lead=%.4f lag=%.4f",
                indexLogic.spreadMA.lastValue,
                indexLogic.spreadMA.getMA(),
                indexLogic.spreadMA.getEwm(),
                indexLogic.spreadMA.getStd(),
                indexLogic.leadPrice,
                indexLogic.lagPrice), SPREAD_STATS, 1);
        controls.publishWithCategory("03. touched EWM/MA " + ind, String.format(Locale.US, "%s %s",
                indexLogic.spreadMA.isEwmTouched(),
                indexLogic.spreadMA.isMaTouched()), SPREAD_STATS, 1);
        controls.publishWithCategory("03. ADV " + ind, String.format(Locale.US, "%s",
                indexLogic.adv), SPREAD_STATS, 1);
        //todo
        controls.publishWithCategory("01. spread " + ind, String.format(Locale.US, "%.4f",
                indexLogic.getTargetPosition(0)), SPREAD_STATS, 1);

        controls.publishWithCategory("02. isSpot " + ind, String.format(Locale.US, "lag/lead %s/%s}",
                indexLogic.isLagSpot, indexLogic.isLeadSpot), SPREAD_STATS, 1);

        log.info(String.format(Locale.US, "SPREAD_LOG %s MA (%.8f %.8f) EWM %.8f STD (%.8f %.8f) %d ", ind,  indexLogic.spreadMA.getMA(), indexLogic.spreadMA.getMASimple(), indexLogic.spreadMA.getEwm(), indexLogic.spreadMA.getStd(), indexLogic.spreadMA.getStdSimple(), indexLogic.spreadMA.actual_size));
        for (String pair : indexLogic.instruments.keySet()) {
            controls.publishWithCategory(String.format(Locale.US, "01. price %s ", pair),  String.format(Locale.US, "%.4f : %.4f", indexLogic.instruments.get(pair).lastValue, indexLogic.weight.get(pair)), SPREAD_STATS, 1);
            log.info(String.format(Locale.US, "PRICE_LOG %s : %.4f : %.4f", pair, indexLogic.instruments.get(pair).lastValue, indexLogic.weight.get(pair)));
        }


        CurrentDealInfo cdi = indexLogic.getCurrentDealInfo();

        if (cdi != null) {
            controls.publishWithCategory(String.format(Locale.US, "03. dealInfo %s ", ind),  String.format(Locale.US, "%s", cdi), SPREAD_STATS, 1);
            //log.info(String.format(Locale.US, "PRICE_LOG %s : %.4f : %.4f", pair, combinationLogic.instruments.get(pair).lastValue, combinationLogic.weight.get(pair)));
        }
    }

    private void publishBalances(Context context) {

        double umBalance = context.getAccountsData().get(accounts.get("BINANCE_FUTURES")).getAccountInfo().getTotalMarginBalance().doubleValue();
        AccountData spotAccountData = context.getAccountsData().get(accounts.get("BINANCE"));
        double spotBalance = spotAccountData.getAssets().get("USDT").getBalance();
        double totalBalance = umBalance + spotBalance;

        if (!isInitBalance) {
            initBalance = totalBalance;
            isInitBalance = true;
        }

        double deltaBalance = totalBalance - initBalance;
        double deltaPercent = (deltaBalance) / initBalance * 100;
        long upTime = System.currentTimeMillis() - strategyStart;
        double deltaPercentMonth = deltaPercent * MONTH_MS / upTime;

        controls.publishWithCategory("01. total cross bal", String.format(Locale.US, "Total = %.4f " + settings.getCoinName() + " (%+.6f " + settings.getCoinName() + " | %+.4f %%)", totalBalance, deltaBalance, deltaPercent), CATEGORY_BALANCES, 1);
        controls.publishWithCategory("02. delta balance", String.format(Locale.US, "%+.4f USDT | month: %+.2f %% | year: %+.2f %%", deltaBalance, deltaPercentMonth, deltaPercentMonth * 12), CATEGORY_BALANCES, 1);
        controls.publishWithCategory("03. balances ", String.format(Locale.US, "spot %+.4f USDT | futures %+.4f USDT", spotBalance, umBalance), CATEGORY_BALANCES, 1);
//        controls.publishWithCategory("03. " + participant.getShortName() + " balance", String.format(Locale.US, "Bal = %.4f %s", slaveBalance, settings.getCoinName()), CATEGORY_BALANCES, 1);
        controls.publishWithCategory("05. Up Time", Duration.ofMillis(upTime), CATEGORY_BALANCES, 1);

        for (String p : combinationLogicMap.keySet()) {
            publishSpreads(combinationLogicMap.get(p), p);
        }

        for (String p : indexParams.keySet()) {

            IndexParameters params = indexParams.get(p);
            controls.publishWithCategory("99. params " + params.getIndexName(), String.format(Locale.US, "%s",
                    params.toString()), "PARAMS", 1);
        }

        for (Index p : ir.indexPositionsUSD.keySet()) {
            controls.publishWithCategory("INDEX " + p.name(), String.format(Locale.US, "%.4f -> %.4f", ir.indexPositionsUSD.get(p), ir.mapTargetAll.get(p)), "INDEX POSITION", 1);

        }

    }
}

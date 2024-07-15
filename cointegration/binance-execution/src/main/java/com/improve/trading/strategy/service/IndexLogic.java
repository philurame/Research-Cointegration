package com.improve.trading.strategy.service;

import com.improve.trading.api.strategy.data.account.Execution;
import com.improve.trading.strategy.commons.advisor.WarmupAdviserData;
import com.improve.trading.strategy.commons.arbitrage.Index;
import com.improve.trading.strategy.commons.arbitrage.Signal;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
public class IndexLogic {
    int size;
    public HashMap<String, MAComputer> instruments = new HashMap<>();
    public HashMap<String, Double> weight = new HashMap<>();
    public HashMap<String, Double> normPrices = new HashMap<>();
    public double leadPrice;
    public double lagPrice;
    public MAComputer spreadMA;
    boolean isReady = false;
    public double targetPosition = 0;
    public boolean isLeadSpot = false;
    public boolean isLagSpot = false;
    public double positionUSD = 0;
    public boolean touchedMA = true;
    double adv = 0;
    List<CurrentDealInfo> dealsHistory = new ArrayList<>();
    public IndexParameters params;
    public double stdThreshold;


    String indexName;

    @Getter
    CurrentDealInfo currentDealInfo = null;
    // lead = master

    ArrayList<Execution> executions = new ArrayList<>();

    // todo переделать
    public void updateSpotStatus(String pair) {
        double w = weight.get(pair);
        if (w > 0) {
            isLeadSpot = true;
        } else {
            isLagSpot = true;
        }
    }
    // _coeffs_mean
    public void normalizeWeightSimple(HashMap<String, Double> weight) {
        double normLead = 0;
        double normLag = 0;
        for (String pair : weight.keySet()) {
            double w = weight.get(pair);
            if (w > 0) {
                normLead += Math.abs(w);
            } else {
                normLag += Math.abs(w);
            }
        }
        for (String pair : weight.keySet()) {
            double w = weight.get(pair);
            if (w > 0) {
                this.weight.put(pair, weight.get(pair) / normLead);
            } else {
                this.weight.put(pair, weight.get(pair) / normLag);
            }
        }
    }


    public IndexLogic(IndexParameters indexParams) {
        params = indexParams;
        this.indexName = indexParams.getIndexName();
        this.size = indexParams.getWindowMA();
        this.stdThreshold = indexParams.getStdThreshold();
        spreadMA = new MAComputer(size, 5000);

        normalizeWeightSimple(indexParams.getWeight());
    }

    public void updateNormalization() {
        for (String pair : weight.keySet()) {
            double[] q = instruments.get(pair).getRawData();
            double normPrice = 0;
            for (double v : q) {
                normPrice += v;
            }
            normPrice /= q.length;
            normPrices.put(pair, normPrice);
        }
    }



    public void warmup(WarmupAdviserData data) {

//        log.info("warmup data: " + data.keySet().stream()
//                .map(key -> key + "=" + Arrays.toString(data.get(key)))
//                .collect(Collectors.joining(", ", "{", "}")));

        for (String pair : weight.keySet()) {
            MAComputer m = new MAComputer(size, 5000);
            instruments.put(pair, m);

            log.info("warmup on {}", pair);
            m.warmup(data.get(pair), Instant.now().toEpochMilli());
        }

        double[] lead = new double[size];
        double[] lag = new double[size];
        double[] spread = new double[size];

        for (int i = 0; i < size; ++i) {
            lead[i] = 0;
            lag[i] = 0;
        }

        updateNormalization();
        int offset = 0;
        for (String pair : weight.keySet()) {
            double w = weight.get(pair);
            double[] q = instruments.get(pair).getRawData();
            double normPrice = normPrices.get(pair);

            offset = Math.min(size, q.length);
            if (w > 0) {
                for (int i = 0; i < offset ; ++i) {
                    lead[i] += w * q[q.length - offset + i] / normPrice;
                }
            } else {
                for (int i = 0; i < offset; ++i) {
                    lag[i] -= w * q[q.length - offset + i] / normPrice;
                }
            }
        }
        for (int i = 0; i < offset; ++i) {
            spread[i] = lead[i] - lag[i];
        }

        leadPrice = lead[offset - 1];
        lagPrice = lag[offset - 1];

        spreadMA.warmup(spread, Instant.now().toEpochMilli());

        isReady = true;
    }

    public void updateExecutions(Collection<Execution> ex) {
        executions.addAll(ex);
    }

    public void updatePrice(String pair, double price, long ts) {

        MAComputer instrument = instruments.get(pair);
        if (!isReady || instrument == null)
            return;
        double w = weight.get(pair);
        double prevPrice = Double.isNaN(instrument.lastValue) ? price : instrument.lastValue;
        double priceDelta = Math.abs(w) * (price - prevPrice) / normPrices.get(pair);
        if (w > 0) {
            leadPrice += priceDelta;
        } else {
            lagPrice += priceDelta;
        }

        spreadMA.update(leadPrice - lagPrice, ts);

//        touchedMA |= spreadMA.isEwmTouched();

        instrument.update(price, ts);
    }

    public static String getIndexString(Index i) {
        return switch (i) {
            case DEFI_LAG, DEFI_LEAD -> "DeFi";
            case PRIVACY_LAG, PRIVACY_LEAD -> "Privacy";
            case NFT_LAG, NFT_LEAD -> "Nft";

            case MATH1_LAG, MATH1_LEAD -> "Math1";
            case MATH2_LAG, MATH2_LEAD -> "Math2";
            // case MATH3_LAG, MATH3_LEAD -> "Math3";
            // case MATH4_LAG, MATH4_LEAD -> "Math4";
            // case MATH5_LAG, MATH5_LEAD -> "Math5";
            case MATH6_LAG, MATH6_LEAD -> "Math6";
            case MATH7_LAG, MATH7_LEAD -> "Math7";
            case MATH8_LAG, MATH8_LEAD -> "Math8";
            // case MATH9_LAG, MATH9_LEAD -> "Math9";
            case MATH10_LAG, MATH10_LEAD -> "Math10";
            case MATH11_LAG, MATH11_LEAD -> "Math11";
            case MATH12_LAG, MATH12_LEAD -> "Math12";
            // case MATH13_LAG, MATH13_LEAD -> "Math13";
            // case MATH14_LAG, MATH14_LEAD -> "Math14";
            // case MATH15_LAG, MATH15_LEAD -> "Math15";
            // case MATH16_LAG, MATH16_LEAD -> "Math16";
            // case MATH17_LAG, MATH17_LEAD -> "Math17";
            case MATH18_LAG, MATH18_LEAD -> "Math18";
            case MATH19_LAG, MATH19_LEAD -> "Math19";
            case MATH20_LAG, MATH20_LEAD -> "Math20";
            case MATH21_LAG, MATH21_LEAD -> "Math21";
            case MATH22_LAG, MATH22_LEAD -> "Math22";
            case MATH23_LAG, MATH23_LEAD -> "Math23";
            case MATH24_LAG, MATH24_LEAD -> "Math24";
            case MATH25_LAG, MATH25_LEAD -> "Math25";
            case MATH26_LAG, MATH26_LEAD -> "Math26";
            case MATH27_LAG, MATH27_LEAD -> "Math27";
            case MATH28_LAG, MATH28_LEAD -> "Math28";
            case MATH29_LAG, MATH29_LEAD -> "Math29";
            case MATH30_LAG, MATH30_LEAD -> "Math30";
            case MATH31_LAG, MATH31_LEAD -> "Math31";
            case MATH32_LAG, MATH32_LEAD -> "Math32";
            case MATH33_LAG, MATH33_LEAD -> "Math33";
            case MATH34_LAG, MATH34_LEAD -> "Math34";
            case MATH35_LAG, MATH35_LEAD -> "Math35";
            case MATH36_LAG, MATH36_LEAD -> "Math36";
            case MATH37_LAG, MATH37_LEAD -> "Math37";
            case MATH38_LAG, MATH38_LEAD -> "Math38";
            case MATH39_LAG, MATH39_LEAD -> "Math39";
            case MATH40_LAG, MATH40_LEAD -> "Math40";

            case NMATH0_LAG, NMATH0_LEAD -> "nMath0";
            case NMATH1_LAG, NMATH1_LEAD -> "nMath1";
            case NMATH2_LAG, NMATH2_LEAD -> "nMath2";
            case NMATH3_LAG, NMATH3_LEAD -> "nMath3";
            case NMATH4_LAG, NMATH4_LEAD -> "nMath4";
            case NMATH5_LAG, NMATH5_LEAD -> "nMath5";
            case NMATH6_LAG, NMATH6_LEAD -> "nMath6";
            case NMATH7_LAG, NMATH7_LEAD -> "nMath7";
            case NMATH8_LAG, NMATH8_LEAD -> "nMath8";
            case NMATH9_LAG, NMATH9_LEAD -> "nMath9";


            case UNKNOWN -> "UNKNOWN";
        };
    }

    public static Index getIndexByString(String indexName, boolean isLag) {
        if ("DeFi".equals(indexName)) {
            return isLag ? Index.DEFI_LAG : Index.DEFI_LEAD;
        } else if ("Privacy".equals(indexName)) {
            return isLag ? Index.PRIVACY_LAG : Index.PRIVACY_LEAD;
        } else if ("Nft".equals(indexName)) {
            return isLag ? Index.NFT_LAG : Index.NFT_LEAD;
        } else if ("Math1".equals(indexName)) {
            return isLag ? Index.MATH1_LAG : Index.MATH1_LEAD;
        } else if ("Math2".equals(indexName)) {
            return isLag ? Index.MATH2_LAG : Index.MATH2_LEAD;
        // } else if ("Math3".equals(indexName)) {
        //     return isLag ? Index.MATH3_LAG : Index.MATH3_LEAD;
        // } else if ("Math4".equals(indexName)) {
        //     return isLag ? Index.MATH4_LAG : Index.MATH4_LEAD;
        // } else if ("Math5".equals(indexName)) {
        //     return isLag ? Index.MATH5_LAG : Index.MATH5_LEAD;
        } else if ("Math6".equals(indexName)) {
            return isLag ? Index.MATH6_LAG : Index.MATH6_LEAD;
        } else if ("Math7".equals(indexName)) {
            return isLag ? Index.MATH7_LAG : Index.MATH7_LEAD;
        } else if ("Math8".equals(indexName)) {
            return isLag ? Index.MATH8_LAG : Index.MATH8_LEAD;
        // } else if ("Math9".equals(indexName)) {
        //     return isLag ? Index.MATH9_LAG : Index.MATH9_LEAD;
        } else if ("Math10".equals(indexName)) {
            return isLag ? Index.MATH10_LAG : Index.MATH10_LEAD;
        } else if ("Math11".equals(indexName)) {
            return isLag ? Index.MATH11_LAG : Index.MATH11_LEAD;
        } else if ("Math12".equals(indexName)) {
            return isLag ? Index.MATH12_LAG : Index.MATH12_LEAD;
        // } else if ("Math13".equals(indexName)) {
        //     return isLag ? Index.MATH13_LAG : Index.MATH13_LEAD;
        // } else if ("Math14".equals(indexName)) {
        //     return isLag ? Index.MATH14_LAG : Index.MATH14_LEAD;
        // } else if ("Math15".equals(indexName)) {
        //     return isLag ? Index.MATH15_LAG : Index.MATH15_LEAD;
        // } else if ("Math16".equals(indexName)) {
        //     return isLag ? Index.MATH16_LAG : Index.MATH16_LEAD;
        // } else if ("Math17".equals(indexName)) {
        //     return isLag ? Index.MATH17_LAG : Index.MATH17_LEAD;
        } else if ("Math18".equals(indexName)) {
            return isLag ? Index.MATH18_LAG : Index.MATH18_LEAD;
        } else if ("Math19".equals(indexName)) {
            return isLag ? Index.MATH19_LAG : Index.MATH19_LEAD;
        } else if ("Math20".equals(indexName)) {
            return isLag ? Index.MATH20_LAG : Index.MATH20_LEAD;
        } else if ("Math21".equals(indexName)) {
            return isLag ? Index.MATH21_LAG : Index.MATH21_LEAD;
        } else if ("Math22".equals(indexName)) {
            return isLag ? Index.MATH22_LAG : Index.MATH22_LEAD;
        } else if ("Math23".equals(indexName)) {
            return isLag ? Index.MATH23_LAG : Index.MATH23_LEAD;
        } else if ("Math24".equals(indexName)) {
            return isLag ? Index.MATH24_LAG : Index.MATH24_LEAD;
        } else if ("Math25".equals(indexName)) {
            return isLag ? Index.MATH25_LAG : Index.MATH25_LEAD;
        } else if ("Math26".equals(indexName)) {
            return isLag ? Index.MATH26_LAG : Index.MATH26_LEAD;
        } else if ("Math27".equals(indexName)) {
            return isLag ? Index.MATH27_LAG : Index.MATH27_LEAD;
        } else if ("Math28".equals(indexName)) {
            return isLag ? Index.MATH28_LAG : Index.MATH28_LEAD;
        } else if ("Math29".equals(indexName)) {
            return isLag ? Index.MATH29_LAG : Index.MATH29_LEAD;
        } else if ("Math30".equals(indexName)) {
            return isLag ? Index.MATH30_LAG : Index.MATH30_LEAD;
        } else if ("Math31".equals(indexName)) {
            return isLag ? Index.MATH31_LAG : Index.MATH31_LEAD;
        } else if ("Math32".equals(indexName)) {
            return isLag ? Index.MATH32_LAG : Index.MATH32_LEAD;
        } else if ("Math33".equals(indexName)) {
            return isLag ? Index.MATH33_LAG : Index.MATH33_LEAD;
        } else if ("Math34".equals(indexName)) {
            return isLag ? Index.MATH34_LAG : Index.MATH34_LEAD;
        } else if ("Math35".equals(indexName)) {
            return isLag ? Index.MATH35_LAG : Index.MATH35_LEAD;
        } else if ("Math36".equals(indexName)) {
            return isLag ? Index.MATH36_LAG : Index.MATH36_LEAD;
        } else if ("Math37".equals(indexName)) {
            return isLag ? Index.MATH37_LAG : Index.MATH37_LEAD;
        } else if ("Math38".equals(indexName)) {
            return isLag ? Index.MATH38_LAG : Index.MATH38_LEAD;
        } else if ("Math39".equals(indexName)) {
            return isLag ? Index.MATH39_LAG : Index.MATH39_LEAD;
        } else if ("Math40".equals(indexName)) {
            return isLag ? Index.MATH40_LAG : Index.MATH40_LEAD;

        } else if ("nMath0".equals(indexName)) {
            return isLag ? Index.NMATH0_LAG : Index.NMATH0_LEAD;
        } else if ("nMath1".equals(indexName)) {
            return isLag ? Index.NMATH1_LAG : Index.NMATH1_LEAD;
        } else if ("nMath2".equals(indexName)) {
            return isLag ? Index.NMATH2_LAG : Index.NMATH2_LEAD;
        } else if ("nMath3".equals(indexName)) {
            return isLag ? Index.NMATH3_LAG : Index.NMATH3_LEAD;
        } else if ("nMath4".equals(indexName)) {
            return isLag ? Index.NMATH4_LAG : Index.NMATH4_LEAD;
        } else if ("nMath5".equals(indexName)) {
            return isLag ? Index.NMATH5_LAG : Index.NMATH5_LEAD;
        } else if ("nMath6".equals(indexName)) {
            return isLag ? Index.NMATH6_LAG : Index.NMATH6_LEAD;
        } else if ("nMath7".equals(indexName)) {
            return isLag ? Index.NMATH7_LAG : Index.NMATH7_LEAD;
        } else if ("nMath8".equals(indexName)) {
            return isLag ? Index.NMATH8_LAG : Index.NMATH8_LEAD;
        } else if ("nMath9".equals(indexName)) {
            return isLag ? Index.NMATH9_LAG : Index.NMATH9_LEAD;
        }
        
        return Index.UNKNOWN;
    }

    public double getTargetPosition(double currentPosition) {
        double defaultSize = params.quoteSize;
        double currentMa = spreadMA.getEwm();
        adv = Math.max(0.001 * Math.max(leadPrice, lagPrice), spreadMA.getStd() * params.cEnter);
        double currentSpread = spreadMA.lastValue;
        positionUSD = currentPosition;
        if (!touchedMA) {
            targetPosition = 0;
            return targetPosition;
        }

        if (currentDealInfo == null) {
            // для мелких std не открываем
            if (spreadMA.getStd() < stdThreshold) {
                targetPosition = 0;
            } else if (currentSpread < spreadMA.ewmValue - adv) {
                targetPosition = defaultSize;
            } else if (currentSpread > spreadMA.ewmValue + adv) {
                targetPosition = -defaultSize;
            } else {
                targetPosition = 0;
            }
            if (isLagSpot) {
                targetPosition = Math.min(0, targetPosition);
            }
            if (isLeadSpot) {
                targetPosition = Math.max(0, targetPosition);
            }

            if (Math.abs(targetPosition) > 0.001) {
                double exitAdv = spreadMA.getStd() * params.cExit;
                double stopAdv = spreadMA.getStd() * params.cStop;
                double stopDeltaAdv = spreadMA.getStd() * Math.max(0, params.cStop - params.cEnter);
                // тут иногда заходим в больший спред, надо стоп пересчитать с учетом этого
                // фиксированные тейк и стоп
                double stopLossSpread = targetPosition > 0 ? Math.min(currentSpread - stopDeltaAdv, currentMa - stopAdv) :
                        Math.max(currentSpread + stopDeltaAdv, currentMa + stopAdv);
                double takeProfitSpread = targetPosition > 0 ? currentMa + exitAdv : currentMa - exitAdv;

                currentDealInfo = new CurrentDealInfo(indexName, currentSpread, targetPosition, takeProfitSpread, stopLossSpread);
                log.info("OpenDeal {}, spread={}", currentDealInfo, currentSpread);

            }
            return targetPosition;
        }

        double stopLossSpread = currentDealInfo.getStopLossSpread();
        double takeProfitSpread = currentDealInfo.getTakeProfitSpread();

        if ((Math.abs(currentPosition) < 0.001) && currentDealInfo.isTriggeredStop() && currentDealInfo.isTriggeredTake()) {
            currentDealInfo = null;
            return targetPosition;
        }

        if (currentDealInfo.isBuy() && (currentSpread < stopLossSpread)) {
            targetPosition = 0;
            touchedMA = false;
            log.info("triggerStop {}, spread={}", currentDealInfo, currentSpread);
            currentDealInfo.setTriggeredStop(true);
        }

        if (!currentDealInfo.isBuy() && (currentSpread > stopLossSpread)) {
            targetPosition = 0;
            touchedMA = false;
            log.info("triggerStop {}, spread={}", currentDealInfo, currentSpread);
            currentDealInfo.setTriggeredStop(true);
        }

        if (currentDealInfo.isBuy() && (currentSpread > takeProfitSpread)) {
            targetPosition = 0;
            log.info("triggerTake {}, spread={}", currentDealInfo, currentSpread);
            currentDealInfo.setTriggeredTake(true);
        }

        if (!currentDealInfo.isBuy() && (currentSpread < takeProfitSpread)) {
            targetPosition = 0;
            log.info("triggerTake {}, spread={}", currentDealInfo, currentSpread);
            currentDealInfo.setTriggeredTake(true);
        }

        if ((currentDealInfo != null) && currentDealInfo.isTriggeredStop()) {
            touchedMA = false;
            spreadMA.setEwmTouched(false);
            spreadMA.setMaTouched(false);
        }

        if (Math.abs(targetPosition) < 0.001) {
            dealsHistory.add(currentDealInfo);
            currentDealInfo = null;
        }

        return targetPosition;
    }
}

package com.improve.trading.strategy.service;

import com.improve.trading.common.util.JsonHelper;
import com.improve.trading.strategy.commons.advisor.WarmupAdviserData;
import com.improve.trading.strategy.service.advisers.CalendarAdviser;
import com.improve.trading.strategy.service.advisers.common.PayloadDeserializer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class AdviserTestLocal {


    public static void test() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMinutes(20))
                .readTimeout(Duration.ofMinutes(20))
                .callTimeout(Duration.ofMinutes(20))
                .build();

        HashMap<String, IndexParameters> params =  StrategyEventProcessor.getParameters();
        HashSet<String> tradingPairsSet = new HashSet<>();
        for (IndexParameters index : params.values()) {
            tradingPairsSet.addAll(index.lags);
            tradingPairsSet.addAll(index.leads);
        }

        String url = "http://10.0.1.3:7777/raw_data?coins=" + String.join(",", tradingPairsSet);
        Request request = new Request.Builder().url(url).build();

        String payload = null;
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (body != null) {
                payload = body.string();
                WarmupAdviserData dto = (WarmupAdviserData)JsonHelper.toEntity(payload, WarmupAdviserData.class);
                for (String a : dto.keySet()) {
                    System.out.println(a);
                    System.out.println(dto.get(a)[0]);
                }
            }
        } catch (Exception e) {
            System.out.println(payload);
            System.out.println(e);
        }

    }

    public static void main(String[] args) {

        test();

    }
}

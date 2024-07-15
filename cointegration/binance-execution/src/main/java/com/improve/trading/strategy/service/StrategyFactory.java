package com.improve.trading.strategy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.improve.trading.api.strategy.*;
import com.improve.trading.api.strategy.data.StrategyConfig;
import com.improve.trading.common.error.FatalException;
import com.improve.trading.strategy.commons.advisor.WarmupAdviserData;
import com.improve.trading.strategy.commons.util.StrategySettings;
import com.improve.trading.strategy.service.advisers.rest.RestGhostAdviser;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StrategyFactory implements CompositeStrategyFactory {

    private final StrategySettings settings;

    private final DiscoveryClient discoveryClient;

    @Override
    public CompositeStrategy create(CompositeEnvironment cenv) {

        HashMap<String, IndexParameters> params =  StrategyEventProcessor.getParameters();
        HashSet<String> tradingPairsSet = new HashSet<>();
        for (IndexParameters index : params.values()) {
            tradingPairsSet.addAll(index.lags);
            tradingPairsSet.addAll(index.leads);
        }

        String requestString = "raw_data?coins=" + String.join(",", tradingPairsSet);


        String url = "http://10.0.1.3:7777/";//"http://10.40.0.198:7777/"; // http://10.40.0.186:7067/
//        getAdviserConfig(cenv.config(), "DataWarmupAdviser").getCustomParameters().get("url");
        CompositeStrategy.Builder builder = CompositeStrategy
                .builder(env -> new StrategyEventProcessor(env, settings))
                .ghostAdviser("DataWarmupAdviser", env -> new RestGhostAdviser(
                        env, () -> url + requestString,
                        WarmupAdviserData.class,
                        Duration.ofMinutes(20),
                        Duration.ofMinutes(15)));

        return builder.create(cenv);
    }


    public static String resolveName(DiscoveryClient discoveryClient, String name) {
        if (Strings.isNullOrEmpty(name)) {
            throw new FatalException("Empty name");
        } else {
            String normalizedName = name.replaceAll("\"", "");
            List<ServiceInstance> instances = discoveryClient.getInstances(normalizedName);
            if (instances.isEmpty()) {
                throw new FatalException("Cant find ServiceInstance for: " + name + (name.equals(normalizedName) ? "" : " (" + normalizedName + ")"));
            } else {
                Map<String, String> metadata = instances.get(0).getMetadata();
                String cloudIp = metadata.get("cloudIp");
                if (!cloudIp.startsWith("ws")) cloudIp = "ws://" + cloudIp;
                return cloudIp + ":" + metadata.get("cloudPort");
            }
        }
    }

    public StrategyConfig.GhostAdviserConfig getAdviserConfig(StrategyConfig config, @NonNull String adviserName) {
        for (StrategyConfig.GhostAdviserConfig conf : config.getGhostAdvisers()) {
            if (adviserName.equalsIgnoreCase(conf.getName())) {
                return conf;
            }
        }
        return null;
    }

    private String getExternalAdviserUrlFromConsul(StrategyConfig.GhostAdviserConfig ac, String postfix) {

        JsonNode customParameters = ac.getCustomParameters();
        JsonNode url = customParameters.get("url");
        if (url != null) {
            return url.toString().replaceAll("\"", "");
        }

        if (customParameters.get("consulName") == null) {
            throw new FatalException("Name for external adviser is empty");
        }

        String name = customParameters.get("consulName").toString().replaceAll("\"", "");

        var instances = discoveryClient.getInstances(name);
        if (instances.isEmpty()) {
            throw new FatalException("Cant find external adviser: " + name);
        }
        var metadata = instances.get(0).getMetadata();
        return "http://" + metadata.get("cloudIp") + ":" + metadata.get("cloudPort") + "/" + postfix;
    }
}
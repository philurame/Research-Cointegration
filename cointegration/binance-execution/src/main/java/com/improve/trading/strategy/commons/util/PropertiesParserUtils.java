package com.improve.trading.strategy.commons.util;

import com.google.common.collect.Maps;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Properties;

public class PropertiesParserUtils {

    public static Map<String, String> tryToParseIncomingProperties(String properties) throws IOException {
        Properties incomingProperties = new Properties();
        incomingProperties.load(new StringReader(properties));
        if (incomingProperties.isEmpty()) {
            throw new IllegalStateException("Cant parse properties. Looks like just a command");
        }
        return Maps.fromProperties(incomingProperties);
    }

}

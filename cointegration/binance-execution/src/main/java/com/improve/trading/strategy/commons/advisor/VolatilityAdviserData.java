package com.improve.trading.strategy.commons.advisor;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Setter
@Getter
public class VolatilityAdviserData {
    @JsonProperty("transaction_ts")
    long transactionTime;

    @JsonProperty("adviser_local_time")
    long advLocalTime;

    @JsonProperty("prediction")
    double prediction;
}

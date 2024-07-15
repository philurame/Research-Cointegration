package com.improve.trading.strategy.commons.advisor;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;

@ToString
@Setter
@Getter
public class WarmupAdviserData extends HashMap<String, double[]> {}
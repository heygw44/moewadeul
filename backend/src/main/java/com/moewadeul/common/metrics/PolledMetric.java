package com.moewadeul.common.metrics;

public interface PolledMetric {

    String job();

    void refresh();
}

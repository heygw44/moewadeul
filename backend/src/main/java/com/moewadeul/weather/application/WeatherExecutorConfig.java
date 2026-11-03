package com.moewadeul.weather.application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WeatherExecutorConfig {

    public static final String WEATHER_EXECUTOR = "weatherExecutor";

    // 기본 close()는 작업이 끝나기를 기다린다. 응답을 포기한 호출이 남아 있어도 종료를 막지 않으려고 shutdown만 부른다.
    @Bean(name = WEATHER_EXECUTOR, destroyMethod = "shutdown")
    ExecutorService weatherExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("weather-", 0).factory());
    }
}

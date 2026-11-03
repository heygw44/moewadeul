package com.moewadeul.weather.application;

class WeatherCacheBusyException extends RuntimeException {

    WeatherCacheBusyException() {
        super("다른 요청이 받는 중인 값을 기다리는 자리가 모두 차 있다.");
    }
}

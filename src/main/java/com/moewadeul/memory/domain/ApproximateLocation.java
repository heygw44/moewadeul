package com.moewadeul.memory.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ApproximateLocation(double latitude, double longitude) {

    private static final int SCALE = 2;

    public static ApproximateLocation of(double lat, double lng) {
        return new ApproximateLocation(round(lat), round(lng));
    }

    // BigDecimal.valueOf는 double의 문자열 표현을 쓰므로, new BigDecimal(double)과 달리 37.565가 2진 오차로 37.56이 되지 않는다.
    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP).doubleValue();
    }
}

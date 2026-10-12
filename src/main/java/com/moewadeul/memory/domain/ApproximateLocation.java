package com.moewadeul.memory.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 정확한 좌표를 소수 둘째 자리(위도 기준 약 1km)로 반올림한 좌표. 기록 작성자가 아닌 사람에게는 정확한 좌표 대신 이 값만 보여 준다.
 *
 * <p>반올림은 0.5를 올리는 방식(HALF_UP)이고, 음수는 0에서 멀어지는 쪽으로 올린다. 입력을 문자열 표현 그대로 십진수로 바꿔 반올림하므로,
 * 2진 부동소수점 오차 때문에 37.565가 37.56으로 내려가는 일은 없다.
 */
public record ApproximateLocation(double latitude, double longitude) {

    private static final int SCALE = 2;

    /** 호출하면 lat, lng를 각각 소수 둘째 자리로 반올림한 좌표를 돌려준다. */
    public static ApproximateLocation of(double lat, double lng) {
        return new ApproximateLocation(round(lat), round(lng));
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP).doubleValue();
    }
}

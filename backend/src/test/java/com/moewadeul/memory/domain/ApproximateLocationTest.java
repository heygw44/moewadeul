package com.moewadeul.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApproximateLocationTest {

    @Test
    @DisplayName("[F-33][MM-08] 소수 셋째 자리 이하는 반올림해서 둘째 자리까지만 남긴다")
    void roundsToTwoDecimals() {
        ApproximateLocation location = ApproximateLocation.of(37.56789, 126.97412);

        assertThat(location.latitude()).isEqualTo(37.57);
        assertThat(location.longitude()).isEqualTo(126.97);
    }

    @Test
    @DisplayName("[F-33][MM-08] 0.005는 올리고, 2진수 오차 때문에 내리지 않는다")
    void roundsHalfUp() {
        assertThat(ApproximateLocation.of(37.565, 127.005).latitude()).isEqualTo(37.57);
        assertThat(ApproximateLocation.of(37.565, 127.005).longitude()).isEqualTo(127.01);
    }

    @Test
    @DisplayName("[F-33][MM-08] 음수 좌표는 0에서 먼 쪽으로 올린다")
    void roundsNegativeAwayFromZero() {
        ApproximateLocation location = ApproximateLocation.of(-33.865, -151.2093);

        assertThat(location.latitude()).isEqualTo(-33.87);
        assertThat(location.longitude()).isEqualTo(-151.21);
    }

    @Test
    @DisplayName("[F-33][MM-08] 위도 90, 경도 -180 같은 경계 값은 그대로 둔다")
    void keepsBoundaryValues() {
        ApproximateLocation location = ApproximateLocation.of(90, -180);

        assertThat(location.latitude()).isEqualTo(90.0);
        assertThat(location.longitude()).isEqualTo(-180.0);
    }
}

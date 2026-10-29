package com.moewadeul.common.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AgeGroupTest {

    @ParameterizedTest
    @CsvSource({
        "19, TWENTIES",
        "29, TWENTIES",
        "30, THIRTIES",
        "39, THIRTIES",
        "40, FORTIES",
        "49, FORTIES",
        "50, FIFTIES",
        "59, FIFTIES",
        "60, SIXTIES_PLUS",
        "100, SIXTIES_PLUS"
    })
    @DisplayName("[NFR-11] 연 나이 19~29는 20대이고 10년 단위로 올라가며 60세부터는 60대 이상이다")
    void groupsByAge(int age, AgeGroup expected) {
        assertThat(AgeGroup.ofAge(age)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"20, TWENTIES", "30, THIRTIES", "40, FORTIES", "50, FIFTIES", "60, SIXTIES_PLUS"})
    @DisplayName("연령대 숫자는 20~60의 십 단위로 정해지고 decade()로 되돌아온다")
    void roundTripsDecade(int decade, AgeGroup expected) {
        assertThat(AgeGroup.ofDecade(decade)).isEqualTo(expected);
        assertThat(expected.decade()).isEqualTo(decade);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 19, 25, 35, 70, 80, -20})
    @DisplayName("연령대 숫자가 20~60의 십 단위가 아니면 거절한다")
    void rejectsInvalidDecade(int decade) {
        assertThatThrownBy(() -> AgeGroup.ofDecade(decade))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("연령대는 20~60 사이의 십 단위여야 합니다.");
    }
}

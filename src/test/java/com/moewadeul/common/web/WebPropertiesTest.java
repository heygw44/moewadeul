package com.moewadeul.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebPropertiesTest {

    @Test
    @DisplayName("기준 주소 끝의 슬래시를 모두 떼어 낸다")
    void stripsTrailingSlashes() {
        assertThat(new WebProperties("https://moewadeul.example///").baseUrl()).isEqualTo("https://moewadeul.example");
    }

    @Test
    @DisplayName("끝에 슬래시가 없는 기준 주소는 그대로 둔다")
    void keepsUrlWithoutTrailingSlash() {
        assertThat(new WebProperties("http://localhost:5173").baseUrl()).isEqualTo("http://localhost:5173");
    }

    @Test
    @DisplayName("기준 주소가 비어 있거나 공백뿐이면 거부한다")
    void rejectsBlank() {
        assertThatThrownBy(() -> new WebProperties(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebProperties("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebProperties(null)).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.moewadeul.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailPropertiesTest {

    @Test
    @DisplayName("메일 한 통의 제한 시간이 0이거나 음수면 거부한다")
    void rejectsNonPositiveSendTimeout() {
        assertThatThrownBy(() -> new MailProperties("a@example.com", "뫼와들", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("moewadeul.mail.sendTimeout must be positive");
        assertThatThrownBy(() -> new MailProperties("a@example.com", "뫼와들", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("발신 주소가 비어 있어도 로컬 설정으로 받아들인다")
    void acceptsEmptyFrom() {
        assertThat(new MailProperties("", "뫼와들", Duration.ofSeconds(20)).from()).isEmpty();
    }
}

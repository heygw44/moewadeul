package com.moewadeul.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.member.application.MailSecretDeriver.Purpose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailSecretDeriverTest {

    private static final String KEY = "unit-test-only-mail-secret-key-0123456789";
    private static final String OTHER_KEY = "other-unit-test-only-mail-secret-key-9876543210";

    private final MailSecretDeriver deriver = new MailSecretDeriver(new MailSecretProperties(KEY));

    @Test
    @DisplayName("[EV-03] 같은 용도·이벤트·회원이면 항상 같은 값이라 재시도 메일에 같은 코드와 링크가 담긴다")
    void sameInputGivesSameSecret() {
        assertThat(deriver.derive(Purpose.PASSWORD_RESET, 7, 3))
                .isEqualTo(deriver.derive(Purpose.PASSWORD_RESET, 7, 3));
    }

    @Test
    @DisplayName("용도, 이벤트, 회원, 키 중 하나라도 다르면 값이 다르다")
    void anyDifferentInputGivesDifferentSecret() {
        byte[] base = deriver.derive(Purpose.PASSWORD_RESET, 7, 3);
        MailSecretDeriver otherKey = new MailSecretDeriver(new MailSecretProperties(OTHER_KEY));

        assertThat(deriver.derive(Purpose.EMAIL_VERIFICATION, 7, 3)).isNotEqualTo(base);
        assertThat(deriver.derive(Purpose.PASSWORD_RESET, 8, 3)).isNotEqualTo(base);
        assertThat(deriver.derive(Purpose.PASSWORD_RESET, 7, 4)).isNotEqualTo(base);
        assertThat(otherKey.derive(Purpose.PASSWORD_RESET, 7, 3)).isNotEqualTo(base);
    }

    @Test
    @DisplayName("키가 비었거나 32바이트보다 짧으면 앱이 시작하지 않고, 예외 메시지와 toString에 키 값이 없다")
    void rejectsMissingOrShortKey() {
        String shortKey = KEY.substring(0, MailSecretProperties.MIN_KEY_BYTES - 1);

        assertThatThrownBy(() -> new MailSecretProperties(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MailSecretProperties("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MailSecretProperties(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(shortKey);
        assertThat(new MailSecretProperties(KEY).toString()).doesNotContain(KEY);
    }
}

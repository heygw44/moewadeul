package com.moewadeul.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EnumParserTest {

    private enum Color {
        RED,
        GREEN,
        BLUE
    }

    private static final String MESSAGE = "색은 RED, GREEN, BLUE 중 하나여야 해요.";
    private static final Set<Color> WARM = EnumSet.of(Color.RED);

    @Test
    @DisplayName("find는 이름이 정확히 같은 상수를 돌려준다")
    void findMatchesExactName() {
        assertThat(EnumParser.find(Color.class, "GREEN")).contains(Color.GREEN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"green", " GREEN", "PURPLE"})
    @DisplayName("find는 null, 빈 문자열, 대소문자나 공백이 다른 이름, 없는 이름이면 비어 있다")
    void findIsEmptyForNonMatching(String name) {
        assertThat(EnumParser.find(Color.class, name)).isEmpty();
    }

    @Test
    @DisplayName("parse는 이름이 맞으면 상수를 돌려준다")
    void parseReturnsConstant() {
        assertThat(EnumParser.parse(Color.class, "BLUE", MESSAGE)).isEqualTo(Color.BLUE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"blue", "PURPLE"})
    @DisplayName("parse는 null이나 맞지 않는 이름이면 주어진 문구의 INVALID_INPUT을 던진다")
    void parseThrowsInvalidInput(String name) {
        assertThatThrownBy(() -> EnumParser.parse(Color.class, name, MESSAGE))
                .satisfies(EnumParserTest::isInvalidInput);
    }

    @Test
    @DisplayName("parseNullable은 null이면 null을, 이름이 맞으면 상수를 돌려준다")
    void parseNullablePassesNullThrough() {
        assertThat(EnumParser.parseNullable(Color.class, null, MESSAGE)).isNull();
        assertThat(EnumParser.parseNullable(Color.class, "RED", MESSAGE)).isEqualTo(Color.RED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "red", "PURPLE"})
    @DisplayName("parseNullable은 null이 아닌데 맞지 않는 이름이면 INVALID_INPUT을 던진다")
    void parseNullableThrowsForNonMatching(String name) {
        assertThatThrownBy(() -> EnumParser.parseNullable(Color.class, name, MESSAGE))
                .satisfies(EnumParserTest::isInvalidInput);
    }

    @Test
    @DisplayName("parseAmong은 허용 집합 안의 이름만 받는다")
    void parseAmongAcceptsOnlyAllowed() {
        assertThat(EnumParser.parseAmong(WARM, "RED", MESSAGE)).isEqualTo(Color.RED);
        assertThatThrownBy(() -> EnumParser.parseAmong(WARM, "GREEN", MESSAGE))
                .satisfies(EnumParserTest::isInvalidInput);
        assertThatThrownBy(() -> EnumParser.parseAmong(WARM, null, MESSAGE)).satisfies(EnumParserTest::isInvalidInput);
    }

    @Test
    @DisplayName("names는 선언 순서대로 쉼표로 이은 이름을 돌려준다")
    void namesJoinsInDeclarationOrder() {
        assertThat(EnumParser.names(Color.class)).isEqualTo("RED, GREEN, BLUE");
    }

    private static void isInvalidInput(Throwable thrown) {
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
            assertThat(e.getMessage()).isEqualTo(MESSAGE);
        });
    }
}

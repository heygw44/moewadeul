package com.moewadeul.basecamp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.common.profile.TrustLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class JoinConditionTest {

    @Test
    @DisplayName("[BC-05] 조건을 걸지 않으면 모든 값이 비어 있고 동성만도 꺼져 있다")
    void noneHasNoCondition() {
        JoinCondition condition = JoinCondition.none();

        assertThat(condition.getMinTrustLevel()).isNull();
        assertThat(condition.getAgeGroupMin()).isNull();
        assertThat(condition.getAgeGroupMax()).isNull();
        assertThat(condition.isSameGenderOnly()).isFalse();
        assertThat(condition.getRequiredGender()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = TrustLevel.class,
            names = {"IDENTITY_VERIFIED", "TRUSTED"})
    @DisplayName("[BC-05] 최소 신뢰 단계는 1 또는 2를 걸 수 있다")
    void acceptsTrustLevel(TrustLevel level) {
        assertThat(JoinCondition.none().withMinTrustLevel(level).getMinTrustLevel())
                .isEqualTo(level);
    }

    @ParameterizedTest
    @EnumSource(
            value = TrustLevel.class,
            names = {"EMAIL_VERIFIED"})
    @DisplayName("[BC-05] 최소 신뢰 단계가 1~2가 아니면 거부한다")
    void rejectsTrustLevel(TrustLevel level) {
        assertThatThrownBy(() -> JoinCondition.none().withMinTrustLevel(level))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[BC-05] 연령대 하한과 상한이 같거나 하한이 더 작으면 걸 수 있다")
    void acceptsAgeGroupRange() {
        JoinCondition same = JoinCondition.none().withAgeGroupRange(AgeGroup.THIRTIES, AgeGroup.THIRTIES);
        JoinCondition range = JoinCondition.none().withAgeGroupRange(AgeGroup.TWENTIES, AgeGroup.SIXTIES_PLUS);

        assertThat(same.getAgeGroupMin()).isEqualTo(AgeGroup.THIRTIES);
        assertThat(same.getAgeGroupMax()).isEqualTo(AgeGroup.THIRTIES);
        assertThat(range.getAgeGroupMin()).isEqualTo(AgeGroup.TWENTIES);
        assertThat(range.getAgeGroupMax()).isEqualTo(AgeGroup.SIXTIES_PLUS);
    }

    @Test
    @DisplayName("[BC-05] 연령대 하한이 상한보다 크면 거부한다")
    void rejectsInvertedAgeRange() {
        assertThatThrownBy(() -> JoinCondition.none().withAgeGroupRange(AgeGroup.FORTIES, AgeGroup.THIRTIES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[BC-05] 동성만 받으면 캠프 리더의 성별이 함께 저장된다")
    void sameGenderOnlyKeepsGender() {
        JoinCondition condition = JoinCondition.none().withSameGenderOnly(Gender.FEMALE);

        assertThat(condition.isSameGenderOnly()).isTrue();
        assertThat(condition.getRequiredGender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("[BC-05] 동성만 받는데 성별이 없으면 거부한다")
    void rejectsSameGenderWithoutGender() {
        assertThatThrownBy(() -> JoinCondition.none().withSameGenderOnly(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[BC-05] 조건을 더해도 이미 건 다른 조건은 그대로 두고 원래 객체는 바뀌지 않는다")
    void withMethodsKeepOtherConditions() {
        JoinCondition base = JoinCondition.none().withMinTrustLevel(TrustLevel.TRUSTED);

        JoinCondition combined =
                base.withAgeGroupRange(AgeGroup.TWENTIES, AgeGroup.THIRTIES).withSameGenderOnly(Gender.MALE);

        assertThat(combined.getMinTrustLevel()).isEqualTo(TrustLevel.TRUSTED);
        assertThat(combined.getAgeGroupMin()).isEqualTo(AgeGroup.TWENTIES);
        assertThat(base.getAgeGroupMin()).isNull();
        assertThat(base.isSameGenderOnly()).isFalse();
    }
}

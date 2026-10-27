package com.moewadeul.basecamp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JoinCondition {

    public static final int MIN_TRUST_LEVEL = 1;
    public static final int MAX_TRUST_LEVEL = 2;
    public static final int MIN_AGE_GROUP = 20;
    public static final int MAX_AGE_GROUP = 60;
    public static final int AGE_GROUP_STEP = 10;

    @Column(name = "min_trust_level")
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer minTrustLevel;

    @Column(name = "age_group_min")
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer ageGroupMin;

    @Column(name = "age_group_max")
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer ageGroupMax;

    @Column(name = "same_gender_only")
    private boolean sameGenderOnly;

    @Column(name = "required_gender")
    @Enumerated(EnumType.STRING)
    private JoinGender requiredGender;

    private JoinCondition(
            Integer minTrustLevel,
            Integer ageGroupMin,
            Integer ageGroupMax,
            boolean sameGenderOnly,
            JoinGender requiredGender) {
        this.minTrustLevel = minTrustLevel;
        this.ageGroupMin = ageGroupMin;
        this.ageGroupMax = ageGroupMax;
        this.sameGenderOnly = sameGenderOnly;
        this.requiredGender = requiredGender;
    }

    public static JoinCondition none() {
        return new JoinCondition(null, null, null, false, null);
    }

    public JoinCondition withMinTrustLevel(int level) {
        if (level < MIN_TRUST_LEVEL || level > MAX_TRUST_LEVEL) {
            throw new IllegalArgumentException("최소 신뢰 단계는 " + MIN_TRUST_LEVEL + " 또는 " + MAX_TRUST_LEVEL + "이어야 합니다.");
        }
        return new JoinCondition(level, ageGroupMin, ageGroupMax, sameGenderOnly, requiredGender);
    }

    public JoinCondition withAgeGroupRange(int min, int max) {
        requireAgeGroup(min);
        requireAgeGroup(max);
        if (min > max) {
            throw new IllegalArgumentException("연령대 하한은 상한보다 클 수 없습니다.");
        }
        return new JoinCondition(minTrustLevel, min, max, sameGenderOnly, requiredGender);
    }

    public JoinCondition withSameGenderOnly(JoinGender gender) {
        if (gender == null) {
            throw new IllegalArgumentException("동성만 받으려면 캠프 리더의 성별이 필요합니다.");
        }
        return new JoinCondition(minTrustLevel, ageGroupMin, ageGroupMax, true, gender);
    }

    private static void requireAgeGroup(int ageGroup) {
        boolean inRange = ageGroup >= MIN_AGE_GROUP && ageGroup <= MAX_AGE_GROUP;
        if (!inRange || ageGroup % AGE_GROUP_STEP != 0) {
            throw new IllegalArgumentException("연령대는 " + MIN_AGE_GROUP + "~" + MAX_AGE_GROUP + " 사이의 십 단위여야 합니다.");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JoinCondition that
                && sameGenderOnly == that.sameGenderOnly
                && Objects.equals(minTrustLevel, that.minTrustLevel)
                && Objects.equals(ageGroupMin, that.ageGroupMin)
                && Objects.equals(ageGroupMax, that.ageGroupMax)
                && requiredGender == that.requiredGender;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minTrustLevel, ageGroupMin, ageGroupMax, sameGenderOnly, requiredGender);
    }
}

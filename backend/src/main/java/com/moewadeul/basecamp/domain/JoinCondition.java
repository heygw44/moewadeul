package com.moewadeul.basecamp.domain;

import com.moewadeul.common.profile.AgeGroup;
import com.moewadeul.common.profile.AgeGroupConverter;
import com.moewadeul.common.profile.Gender;
import com.moewadeul.common.profile.TrustLevel;
import com.moewadeul.common.profile.TrustLevelConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

    @Column(name = "min_trust_level")
    @Convert(converter = TrustLevelConverter.class)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private TrustLevel minTrustLevel;

    @Column(name = "age_group_min")
    @Convert(converter = AgeGroupConverter.class)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private AgeGroup ageGroupMin;

    @Column(name = "age_group_max")
    @Convert(converter = AgeGroupConverter.class)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private AgeGroup ageGroupMax;

    @Column(name = "same_gender_only")
    private boolean sameGenderOnly;

    @Column(name = "required_gender")
    @Enumerated(EnumType.STRING)
    private Gender requiredGender;

    private JoinCondition(
            TrustLevel minTrustLevel,
            AgeGroup ageGroupMin,
            AgeGroup ageGroupMax,
            boolean sameGenderOnly,
            Gender requiredGender) {
        this.minTrustLevel = minTrustLevel;
        this.ageGroupMin = ageGroupMin;
        this.ageGroupMax = ageGroupMax;
        this.sameGenderOnly = sameGenderOnly;
        this.requiredGender = requiredGender;
    }

    public static JoinCondition none() {
        return new JoinCondition(null, null, null, false, null);
    }

    public JoinCondition withMinTrustLevel(TrustLevel level) {
        if (level != TrustLevel.IDENTITY_VERIFIED && level != TrustLevel.TRUSTED) {
            throw new IllegalArgumentException("최소 신뢰 단계는 " + TrustLevel.IDENTITY_VERIFIED.level() + " 또는 "
                    + TrustLevel.TRUSTED.level() + "이어야 합니다.");
        }
        return new JoinCondition(level, ageGroupMin, ageGroupMax, sameGenderOnly, requiredGender);
    }

    public JoinCondition withAgeGroupRange(AgeGroup min, AgeGroup max) {
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("연령대 하한은 상한보다 클 수 없습니다.");
        }
        return new JoinCondition(minTrustLevel, min, max, sameGenderOnly, requiredGender);
    }

    public JoinCondition withSameGenderOnly(Gender gender) {
        if (gender == null) {
            throw new IllegalArgumentException("동성만 받으려면 캠프 리더의 성별이 필요합니다.");
        }
        return new JoinCondition(minTrustLevel, ageGroupMin, ageGroupMax, true, gender);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JoinCondition that
                && sameGenderOnly == that.sameGenderOnly
                && minTrustLevel == that.minTrustLevel
                && ageGroupMin == that.ageGroupMin
                && ageGroupMax == that.ageGroupMax
                && requiredGender == that.requiredGender;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minTrustLevel, ageGroupMin, ageGroupMax, sameGenderOnly, requiredGender);
    }
}

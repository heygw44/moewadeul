package com.moewadeul.trust.domain.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "identity_verification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityVerification {

    public static final int ADULT_AGE = 19;

    public static final Duration CI_RETENTION = Duration.ofDays(30);

    public static final Duration CI_RETENTION_WITH_SANCTION = Duration.ofDays(365);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private long memberId;

    @Column(name = "birth_year")
    private Short birthYear;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Convert(converter = CiHashConverter.class)
    @Column(name = "ci_hash")
    private CiHash ciHash;

    @Enumerated(EnumType.STRING)
    private IdentityProviderType provider;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "ci_retained_until")
    private Instant ciRetainedUntil;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private IdentityVerification(
            long memberId, VerifiedIdentity identity, CiHash ciHash, IdentityProviderType provider, Instant now) {
        this.memberId = memberId;
        this.birthYear = (short) identity.birthYear();
        this.gender = identity.gender();
        this.ciHash = ciHash;
        this.provider = provider;
        this.verifiedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static IdentityVerification verify(
            long memberId, VerifiedIdentity identity, CiHash ciHash, IdentityProviderType provider, Instant now) {
        if (identity == null || ciHash == null || provider == null || now == null) {
            throw new IllegalArgumentException("본인확인 기록을 만드는 데 필요한 값이 null입니다.");
        }
        if (identity.gender() == null) {
            throw new IllegalArgumentException("본인확인 기록에는 성별이 필요합니다.");
        }
        if (identity.birthYear() < Short.MIN_VALUE || identity.birthYear() > Short.MAX_VALUE) {
            throw new IllegalArgumentException("출생연도가 범위를 벗어났습니다.");
        }
        return new IdentityVerification(memberId, identity, ciHash, provider, now);
    }

    public static Instant ciRetainedUntil(Instant withdrawnAt, boolean hasSanctionHistory) {
        return withdrawnAt.plus(hasSanctionHistory ? CI_RETENTION_WITH_SANCTION : CI_RETENTION);
    }

    public void withdraw(Instant retainedUntil, Instant now) {
        if (retainedUntil == null || now == null) {
            throw new IllegalArgumentException("보관 기한이나 수정 시각이 null입니다.");
        }
        this.birthYear = null;
        this.gender = null;
        this.ciRetainedUntil = retainedUntil;
        this.updatedAt = now;
    }

    public void extendRetentionForSanction(Instant withdrawnAt, Instant now) {
        if (withdrawnAt == null || now == null) {
            throw new IllegalArgumentException("탈퇴 시각이나 수정 시각이 null입니다.");
        }
        if (ciRetainedUntil == null) {
            return;
        }
        Instant extended = ciRetainedUntil(withdrawnAt, true);
        if (!ciRetainedUntil.isBefore(extended)) {
            return;
        }
        this.ciRetainedUntil = extended;
        this.updatedAt = now;
    }

    public boolean isAdult(Year currentYear) {
        return birthYear != null && birthYear + ADULT_AGE <= currentYear.getValue();
    }

    public Optional<AgeGroup> ageGroupIn(Year currentYear) {
        if (birthYear == null) {
            return Optional.empty();
        }
        return AgeGroup.fromAge(currentYear.getValue() - birthYear);
    }
}

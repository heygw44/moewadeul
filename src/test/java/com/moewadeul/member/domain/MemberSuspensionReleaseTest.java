package com.moewadeul.member.domain;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberSuspensionReleaseTest {

    private static final Instant NOW = Instant.parse("2026-10-05T03:00:00Z");

    @Test
    @DisplayName("[SN-10] 영구 정지는 종료 시각 없이 SUSPENDED가 되고, 이미 기간이 있는 정지의 종료 시각도 지운다")
    void suspendsPermanently() {
        Member active = memberWith(MemberStatus.ACTIVE, null, NOW);
        Member timed = memberWith(MemberStatus.SUSPENDED, NOW.plus(Duration.ofDays(3)), NOW);

        active.suspendPermanently(NOW);
        timed.suspendPermanently(NOW);

        assertThat(active.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(active.getSuspendedUntil()).isNull();
        assertThat(timed.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(timed.getSuspendedUntil()).isNull();
    }

    @Test
    @DisplayName("[SN-10] 탈퇴한 회원은 영구 정지해도 그대로 둔다")
    void keepsWithdrawnMember() {
        Member member = memberWith(MemberStatus.WITHDRAWN, null, null);

        member.suspendPermanently(NOW);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("[SN-10] 정지 종료 시각이 지났거나 정각이면 정지를 풀고 true를 돌려준다")
    void releasesExpiredSuspension() {
        Member past = memberWith(MemberStatus.SUSPENDED, NOW.minusSeconds(1), NOW.minus(Duration.ofDays(7)));
        Member exact = memberWith(MemberStatus.SUSPENDED, NOW, NOW.minus(Duration.ofDays(7)));

        assertThat(past.releaseSuspensionIfExpired(NOW)).isTrue();
        assertThat(exact.releaseSuspensionIfExpired(NOW)).isTrue();

        assertThat(past.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(past.getSuspendedUntil()).isNull();
        assertThat(past.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[SN-10] 이메일 인증을 마치지 못한 회원은 정지가 풀려도 UNVERIFIED로 돌아간다")
    void unverifiedMemberReturnsToUnverified() {
        Member member = memberWith(MemberStatus.SUSPENDED, NOW.minusSeconds(1), null);

        assertThat(member.releaseSuspensionIfExpired(NOW)).isTrue();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.UNVERIFIED);
    }

    @Test
    @DisplayName("[SN-10] 아직 끝나지 않은 정지, 종료 시각이 없는 영구 정지, 정지 중이 아닌 회원은 바꾸지 않고 false를 돌려준다")
    void keepsOtherMembers() {
        Member notEnded = memberWith(MemberStatus.SUSPENDED, NOW.plusSeconds(1), NOW);
        Member permanent = memberWith(MemberStatus.SUSPENDED, null, NOW);
        Member active = memberWith(MemberStatus.ACTIVE, null, NOW);

        assertThat(notEnded.releaseSuspensionIfExpired(NOW)).isFalse();
        assertThat(permanent.releaseSuspensionIfExpired(NOW)).isFalse();
        assertThat(active.releaseSuspensionIfExpired(NOW)).isFalse();

        assertThat(notEnded.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(permanent.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(active.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("시각이 null이면 거부한다")
    void rejectsNull() {
        Member member = memberWith(MemberStatus.ACTIVE, null, NOW);

        assertThatThrownBy(() -> member.suspendPermanently(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.releaseSuspensionIfExpired(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Member memberWith(MemberStatus status, Instant suspendedUntil, Instant emailVerifiedAt) {
        return aMember()
                .status(status)
                .suspendedUntil(suspendedUntil)
                .emailVerifiedAt(emailVerifiedAt)
                .build();
    }

    @Test
    @DisplayName("[SN-10] 정지가 끝났는지는 정지 중이고 종료 시각이 now 이전이거나 정각일 때만 참이다")
    void isSuspensionOverAt() {
        assertThat(memberWith(MemberStatus.ACTIVE, null, NOW).isSuspensionOverAt(NOW))
                .isFalse();
        assertThat(memberWith(MemberStatus.SUSPENDED, null, NOW).isSuspensionOverAt(NOW))
                .isFalse();
        assertThat(memberWith(MemberStatus.SUSPENDED, NOW.plusSeconds(1), NOW).isSuspensionOverAt(NOW))
                .isFalse();
        assertThat(memberWith(MemberStatus.SUSPENDED, NOW, NOW).isSuspensionOverAt(NOW))
                .isTrue();
        assertThat(memberWith(MemberStatus.SUSPENDED, NOW.minusSeconds(1), NOW).isSuspensionOverAt(NOW))
                .isTrue();
    }

    @Test
    @DisplayName("[ID-01] 이메일 인증 여부는 인증 시각으로 판단하고, 인증 대기는 UNVERIFIED 상태일 때만 참이다")
    void emailVerificationJudgments() {
        Member verified = memberWith(MemberStatus.ACTIVE, null, NOW);
        Member unverified = memberWith(MemberStatus.UNVERIFIED, null, null);
        Member suspendedBeforeVerifying = memberWith(MemberStatus.SUSPENDED, NOW.plusSeconds(60), null);
        Member suspendedAfterVerifying = memberWith(MemberStatus.SUSPENDED, NOW.plusSeconds(60), NOW);

        assertThat(verified.isEmailVerified()).isTrue();
        assertThat(unverified.isEmailVerified()).isFalse();
        assertThat(suspendedBeforeVerifying.isEmailVerified()).isFalse();
        assertThat(suspendedAfterVerifying.isEmailVerified()).isTrue();

        assertThat(unverified.isAwaitingEmailVerification()).isTrue();
        assertThat(verified.isAwaitingEmailVerification()).isFalse();
        assertThat(suspendedBeforeVerifying.isAwaitingEmailVerification()).isFalse();
        assertThat(memberWith(MemberStatus.WITHDRAWN, null, null).isAwaitingEmailVerification())
                .isFalse();
    }
}

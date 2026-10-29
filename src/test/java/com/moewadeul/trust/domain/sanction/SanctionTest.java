package com.moewadeul.trust.domain.sanction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.trust.domain.TrustErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SanctionTest {

    private static final Instant NOW = Instant.parse("2026-10-05T03:00:00Z");

    @Test
    @DisplayName("[SN-05] 임시 정지는 시작 시각부터 72시간이고, 단계와 만든 관리자 없이 적용 중 상태로 만들어진다")
    void temporarySuspensionLastsSeventyTwoHours() {
        Sanction sanction = Sanction.temporary(11L, 5L, NOW);

        assertThat(sanction.getMemberId()).isEqualTo(11L);
        assertThat(sanction.getReportId()).isEqualTo(5L);
        assertThat(sanction.getType()).isEqualTo(SanctionType.TEMPORARY_72H);
        assertThat(sanction.getLevel()).isNull();
        assertThat(sanction.getStartsAt()).isEqualTo(NOW);
        assertThat(sanction.getEndsAt()).isEqualTo(NOW.plus(Duration.ofHours(72)));
        assertThat(sanction.getStatus()).isEqualTo(SanctionStatus.ACTIVE);
        assertThat(sanction.getCreatedBy()).isNull();
        assertThat(sanction.getReason()).isNotBlank();
    }

    @Test
    @DisplayName("시작 시각이 null이면 임시 정지를 만들지 못한다")
    void rejectsNullStart() {
        assertThatThrownBy(() -> Sanction.temporary(11L, 5L, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[SN-10] 경고는 끝나는 시각 없이 1단계로 만들어지고 신고와 관리자를 기록한다")
    void warningHasNoEnd() {
        Sanction sanction = Sanction.confirm(11L, 5L, SanctionType.WARNING, 1, "욕설", NOW, 99L);

        assertThat(sanction.getType()).isEqualTo(SanctionType.WARNING);
        assertThat(sanction.getLevel()).isEqualTo((byte) 1);
        assertThat(sanction.getReportId()).isEqualTo(5L);
        assertThat(sanction.getReason()).isEqualTo("욕설");
        assertThat(sanction.getStartsAt()).isEqualTo(NOW);
        assertThat(sanction.getEndsAt()).isNull();
        assertThat(sanction.getStatus()).isEqualTo(SanctionStatus.ACTIVE);
        assertThat(sanction.getCreatedBy()).isEqualTo(99L);
    }

    @Test
    @DisplayName("[SN-10] 7일 정지는 시작 시각에서 7일, 30일 정지는 30일 뒤에 끝난다")
    void suspensionEndsAfterItsPeriod() {
        Sanction seven = Sanction.confirm(11L, null, SanctionType.SUSPEND_7D, 2, "반복 위반", NOW, 99L);
        Sanction thirty = Sanction.confirm(11L, null, SanctionType.SUSPEND_30D, 3, "반복 위반", NOW, 99L);

        assertThat(seven.getEndsAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(seven.getReportId()).isNull();
        assertThat(thirty.getEndsAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("[SN-10] 영구 정지는 4단계이고 끝나는 시각이 없다")
    void permanentHasNoEnd() {
        Sanction sanction = Sanction.confirm(11L, null, SanctionType.PERMANENT, 4, "심각한 위반", NOW, 99L);

        assertThat(sanction.getLevel()).isEqualTo((byte) 4);
        assertThat(sanction.getEndsAt()).isNull();
    }

    @Test
    @DisplayName("확정 제재는 임시 정지 종류, 종류와 맞지 않는 단계, 빈 사유나 500자를 넘는 사유, null 시작 시각을 거부한다")
    void confirmRejectsInvalidValues() {
        assertThatThrownBy(() -> Sanction.confirm(11L, null, SanctionType.TEMPORARY_72H, 1, "사유", NOW, 99L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sanction.confirm(11L, null, SanctionType.WARNING, 2, "사유", NOW, 99L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sanction.confirm(11L, null, SanctionType.WARNING, 1, " ", NOW, 99L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sanction.confirm(11L, null, SanctionType.WARNING, 1, "가".repeat(501), NOW, 99L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sanction.confirm(11L, null, SanctionType.WARNING, 1, "사유", null, 99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[SN-15] 조기 종료로 해제하면 ENDED_EARLY가 되고 해제한 관리자와 시각을 남긴다")
    void endEarlyRecordsAdminAndTime() {
        Sanction sanction = Sanction.confirm(11L, null, SanctionType.SUSPEND_7D, 2, "반복 위반", NOW, 99L);
        Instant later = NOW.plusSeconds(3600);

        sanction.lift(98L, SanctionLiftReason.END_EARLY, later);

        assertThat(sanction.getStatus()).isEqualTo(SanctionStatus.ENDED_EARLY);
        assertThat(sanction.getLiftedBy()).isEqualTo(98L);
        assertThat(sanction.getLiftedAt()).isEqualTo(later);
        assertThat(sanction.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("[SN-15] 처분 취소로 해제하면 REVOKED가 되고 해제한 관리자와 시각을 남긴다")
    void revokeRecordsAdminAndTime() {
        Sanction sanction = Sanction.confirm(11L, null, SanctionType.SUSPEND_7D, 2, "반복 위반", NOW, 99L);
        Instant later = NOW.plusSeconds(3600);

        sanction.lift(98L, SanctionLiftReason.REVOKE, later);

        assertThat(sanction.getStatus()).isEqualTo(SanctionStatus.REVOKED);
        assertThat(sanction.getLiftedBy()).isEqualTo(98L);
        assertThat(sanction.getLiftedAt()).isEqualTo(later);
        assertThat(sanction.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("[SN-15] 이미 해제됐거나 기간이 끝난 제재는 해제하지 못한다")
    void cannotLiftInactiveSanction() {
        Sanction endedEarly = Sanction.confirm(11L, null, SanctionType.WARNING, 1, "욕설", NOW, 99L);
        endedEarly.lift(98L, SanctionLiftReason.END_EARLY, NOW);
        Sanction revoked = Sanction.confirm(11L, null, SanctionType.WARNING, 1, "욕설", NOW, 99L);
        revoked.lift(98L, SanctionLiftReason.REVOKE, NOW);
        Sanction expired = Sanction.confirm(11L, null, SanctionType.SUSPEND_7D, 2, "반복 위반", NOW, 99L);
        ReflectionTestUtils.setField(expired, "status", SanctionStatus.EXPIRED);

        for (Sanction inactive : List.of(endedEarly, revoked, expired)) {
            assertThatThrownBy(() -> inactive.lift(97L, SanctionLiftReason.REVOKE, NOW))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(TrustErrorCode.SANCTION_INVALID_STATE));
        }
        assertThat(expired.getLiftedBy()).isNull();
        assertThat(endedEarly.getStatus()).isEqualTo(SanctionStatus.ENDED_EARLY);
        assertThat(revoked.getStatus()).isEqualTo(SanctionStatus.REVOKED);
    }

    @Test
    @DisplayName("[SN-05] 기각한 신고의 임시 정지는 적용 중이든 이미 만료됐든 조기 종료됐든 REVOKED가 되고 기각한 관리자와 시각을 남긴다")
    void liftForDismissedReportRevokesActiveExpiredAndEndedEarlyTemporary() {
        Sanction active = Sanction.temporary(11L, 5L, NOW);
        Sanction expired = Sanction.temporary(11L, 6L, NOW);
        ReflectionTestUtils.setField(expired, "status", SanctionStatus.EXPIRED);
        Sanction endedEarly = Sanction.temporary(11L, 7L, NOW);
        endedEarly.lift(97L, SanctionLiftReason.END_EARLY, NOW);
        Instant later = NOW.plusSeconds(3600 * 100);

        active.liftForDismissedReport(98L, later);
        expired.liftForDismissedReport(98L, later);
        endedEarly.liftForDismissedReport(98L, later);

        for (Sanction sanction : List.of(active, expired, endedEarly)) {
            assertThat(sanction.getStatus()).isEqualTo(SanctionStatus.REVOKED);
            assertThat(sanction.getLiftedBy()).isEqualTo(98L);
            assertThat(sanction.getLiftedAt()).isEqualTo(later);
        }
    }

    @Test
    @DisplayName("[SN-05] 이미 취소된 임시 정지와 임시 정지가 아닌 제재는 기각으로 해제하지 못한다")
    void liftForDismissedReportRejectsRevokedOrConfirmedSanction() {
        Sanction lifted = Sanction.temporary(11L, 5L, NOW);
        lifted.liftForDismissedReport(98L, NOW);
        Sanction warning = Sanction.confirm(11L, null, SanctionType.WARNING, 1, "욕설", NOW, 99L);

        assertThatThrownBy(() -> lifted.liftForDismissedReport(98L, NOW))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TrustErrorCode.SANCTION_INVALID_STATE));
        assertThatThrownBy(() -> warning.liftForDismissedReport(98L, NOW))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TrustErrorCode.SANCTION_INVALID_STATE));
        assertThat(warning.getStatus()).isEqualTo(SanctionStatus.ACTIVE);
    }

    @Test
    @DisplayName("[SN-15] 해제 시각이 null이면 거부한다")
    void liftRejectsNullTime() {
        Sanction sanction = Sanction.temporary(11L, 5L, NOW);

        assertThatThrownBy(() -> sanction.lift(98L, SanctionLiftReason.END_EARLY, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[SN-15] 해제 사유가 null이면 거부하고 제재는 그대로 적용 중이다")
    void liftRejectsNullReason() {
        Sanction sanction = Sanction.confirm(11L, null, SanctionType.SUSPEND_7D, 2, "반복 위반", NOW, 99L);

        assertThatThrownBy(() -> sanction.lift(98L, null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(sanction.getStatus()).isEqualTo(SanctionStatus.ACTIVE);
    }

    @Test
    @DisplayName("[SN-10][TR-01][PV-05] 임시 정지는 어떤 상태여도 제재 이력이 아니다")
    void temporarySuspensionNeverCountsAsHistory() {
        for (SanctionStatus status : SanctionStatus.values()) {
            assertThat(sanctionOf(SanctionType.TEMPORARY_72H, status).countsAsHistory())
                    .as("TEMPORARY_72H %s", status)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("[SN-10][TR-01][PV-05] 확정 제재는 적용 중이든 만료됐든 조기 종료됐든 이력이고, 처분 취소됐을 때만 이력이 아니다")
    void confirmedSanctionCountsAsHistoryUnlessRevoked() {
        for (SanctionType type : SanctionType.values()) {
            if (type == SanctionType.TEMPORARY_72H) {
                continue;
            }
            for (SanctionStatus status : SanctionStatus.values()) {
                assertThat(sanctionOf(type, status).countsAsHistory())
                        .as("%s %s", type, status)
                        .isEqualTo(status != SanctionStatus.REVOKED);
            }
        }
    }

    @Test
    @DisplayName("[PV-05] 탈퇴 CI 보관은 임시 정지를 포함한 모든 제재를 세고, 처분 취소된 제재만 세지 않는다")
    void everySanctionCountsForCiRetentionUnlessRevoked() {
        for (SanctionType type : SanctionType.values()) {
            for (SanctionStatus status : SanctionStatus.values()) {
                assertThat(sanctionOf(type, status).countsForCiRetention())
                        .as("%s %s", type, status)
                        .isEqualTo(status != SanctionStatus.REVOKED);
            }
        }
    }

    private static Sanction sanctionOf(SanctionType type, SanctionStatus status) {
        Sanction sanction = Sanction.temporary(11L, 5L, NOW);
        ReflectionTestUtils.setField(sanction, "type", type);
        ReflectionTestUtils.setField(sanction, "status", status);
        return sanction;
    }
}

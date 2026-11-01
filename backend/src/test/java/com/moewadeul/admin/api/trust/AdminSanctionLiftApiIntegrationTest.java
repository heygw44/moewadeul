package com.moewadeul.admin.api.trust;

import static com.moewadeul.admin.api.trust.AdminReportApiFixture.SANCTIONS;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.trust.application.CompanionReviewFixture;
import com.moewadeul.trust.application.report.MemberReportActionService;
import com.moewadeul.trust.application.report.MemberReportFixture;
import com.moewadeul.trust.application.report.MemberReportService;
import com.moewadeul.trust.application.sanction.SanctionFixture;
import com.moewadeul.trust.application.withdrawal.WithdrawalService;
import com.moewadeul.trust.domain.sanction.SanctionType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class AdminSanctionLiftApiIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberReportService memberReportService;

    @Autowired
    private MemberReportActionService memberReportActionService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private WithdrawalService withdrawalService;

    private AdminReportApiFixture api;
    private SanctionFixture sanctionFixture;

    @BeforeEach
    void setUp() {
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        MemberReportFixture reportFixture = new MemberReportFixture(jdbc);
        api = new AdminReportApiFixture(
                mvc, passwordEncoder, jdbc, fixture, reportFixture, memberReportService, withdrawalService);
        sanctionFixture = new SanctionFixture(fixture, reportFixture, memberReportActionService, clock);
    }

    @Test
    @DisplayName("[SN-15] 7일 정지를 처분 취소하면 로그인할 수 있고 다음 제재 단계는 해제 전 단계로 돌아간다")
    void revokeSuspensionAllowsLoginAndRestoresNextLevel() {
        confirm(SanctionType.WARNING);
        long sanctionId = confirm(SanctionType.SUSPEND_7D);
        assertThat(api.loginRequest(api.targetId)).hasStatus(HttpStatus.FORBIDDEN);

        MvcTestResult result = api.lift(sanctionId, "REVOKE");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("REVOKED");
        assertThat(result).bodyJson().extractingPath("$.sanctionId").isEqualTo((int) sanctionId);
        assertThat(api.memberStatus(api.targetId)).isEqualTo("UNVERIFIED");
        assertThat(api.loginRequest(api.targetId)).hasStatus(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(status, ':', lifted_by) FROM sanction WHERE id = ?", String.class, sanctionId))
                .isEqualTo("REVOKED:" + api.adminId);
        assertThat(confirm(SanctionType.SUSPEND_7D)).isNotEqualTo(sanctionId);
        assertThat(jdbc.queryForObject(
                        "SELECT detail FROM admin_audit_log WHERE action = 'SANCTION_LIFT'", String.class))
                .contains("\"sanctionType\": \"SUSPEND_7D\"")
                .contains("\"toStatus\": \"REVOKED\"");
        assertThat(jdbc.queryForObject(
                        "SELECT target_id FROM admin_audit_log WHERE action = 'SANCTION_LIFT'", Long.class))
                .isEqualTo(sanctionId);
    }

    @Test
    @DisplayName("[SN-15] 7일 정지를 조기 종료하면 로그인할 수 있고 제재는 ENDED_EARLY로 이력에 남는다")
    void endEarlySuspensionAllowsLogin() {
        confirm(SanctionType.WARNING);
        long sanctionId = confirm(SanctionType.SUSPEND_7D);

        MvcTestResult result = api.lift(sanctionId, "END_EARLY");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ENDED_EARLY");
        assertThat(api.memberStatus(api.targetId)).isEqualTo("UNVERIFIED");
        assertThat(api.loginRequest(api.targetId)).hasStatus(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(status, ':', lifted_by) FROM sanction WHERE id = ?", String.class, sanctionId))
                .isEqualTo("ENDED_EARLY:" + api.adminId);
        assertThat(jdbc.queryForObject(
                        "SELECT detail FROM admin_audit_log WHERE action = 'SANCTION_LIFT'", String.class))
                .contains("\"toStatus\": \"ENDED_EARLY\"");
    }

    @Test
    @DisplayName("[SN-10][SN-15] 30일 정지를 조기 종료하면 다음 위반은 영구 정지로 한 단계 높게 확정된다")
    void nextLevelRisesAfterEndedEarly() {
        confirm(SanctionType.WARNING);
        confirm(SanctionType.SUSPEND_7D);
        long thirtyDays = confirm(SanctionType.SUSPEND_30D);

        assertThat(api.lift(thirtyDays, "END_EARLY")).hasStatus(HttpStatus.OK);

        assertThat(confirm(SanctionType.PERMANENT)).isNotEqualTo(thirtyDays);
        assertThat(jdbc.queryForObject("SELECT MAX(level) FROM sanction", Integer.class))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("[SN-10][SN-15] 30일 정지를 처분 취소하면 다음 위반은 같은 30일 정지로 다시 확정된다")
    void nextLevelRepeatsAfterRevoked() {
        confirm(SanctionType.WARNING);
        confirm(SanctionType.SUSPEND_7D);
        long thirtyDays = confirm(SanctionType.SUSPEND_30D);

        assertThat(api.lift(thirtyDays, "REVOKE")).hasStatus(HttpStatus.OK);

        assertThat(confirm(SanctionType.SUSPEND_30D)).isNotEqualTo(thirtyDays);
        assertThat(api.count("sanction WHERE type = 'SUSPEND_30D'")).isEqualTo(2);
    }

    @Test
    @DisplayName("[SN-15] 해제 사유가 없거나 올바르지 않으면 400 INVALID_INPUT이고 제재는 그대로 적용 중이다")
    void liftRequiresValidReason() {
        long sanctionId = confirm(SanctionType.WARNING);

        MvcTestResult missingBody = api.post(SANCTIONS + "/" + sanctionId + "/lift", null);
        MvcTestResult missingReason = api.post(SANCTIONS + "/" + sanctionId + "/lift", "{}");
        MvcTestResult unknownReason = api.lift(sanctionId, "LIFTED");

        for (MvcTestResult result : List.of(missingBody, missingReason, unknownReason)) {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        assertThat(api.sanctionStatus(sanctionId)).isEqualTo("ACTIVE");
        assertThat(api.count("admin_audit_log WHERE action = 'SANCTION_LIFT'")).isZero();
    }

    @Test
    @DisplayName("[SN-15] 영구 정지를 해제하면 로그인할 수 있다")
    void liftPermanentSuspension() {
        long sanctionId = confirm(SanctionType.PERMANENT);

        assertThat(api.lift(sanctionId, "REVOKE")).hasStatus(HttpStatus.OK);

        assertThat(api.memberStatus(api.targetId)).isEqualTo("UNVERIFIED");
        assertThat(api.loginRequest(api.targetId)).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[SN-15] 경고를 처분 취소해도 회원 상태는 그대로이고 다음 제재는 다시 경고부터다")
    void revokeWarning() {
        long sanctionId = confirm(SanctionType.WARNING);

        assertThat(api.lift(sanctionId, "REVOKE")).hasStatus(HttpStatus.OK);

        assertThat(api.memberStatus(api.targetId)).isEqualTo("UNVERIFIED");
        assertThat(confirm(SanctionType.WARNING)).isNotEqualTo(sanctionId);
    }

    @Test
    @DisplayName("[SN-15] 두 정지 중 더 늦게 끝나는 정지를 해제하면 종료 시각이 남은 정지로 줄어든다")
    void liftLongerSuspensionShortensUntil() {
        confirm(SanctionType.WARNING);
        confirm(SanctionType.SUSPEND_7D);
        Instant sevenDaysEnd = api.suspendedUntil(api.targetId);
        long thirtyDays = confirm(SanctionType.SUSPEND_30D);
        assertThat(api.suspendedUntil(api.targetId)).isAfter(sevenDaysEnd);

        api.lift(thirtyDays, "END_EARLY");

        assertThat(api.memberStatus(api.targetId)).isEqualTo("SUSPENDED");
        assertThat(api.suspendedUntil(api.targetId)).isEqualTo(sevenDaysEnd);
    }

    @Test
    @DisplayName("[SN-15] 이미 해제됐거나 기간이 끝난 제재를 해제하면 409 SANCTION_INVALID_STATE이다")
    void liftInactiveSanctionIsConflict() {
        long lifted = confirm(SanctionType.WARNING);
        api.lift(lifted, "REVOKE");
        long expired = confirm(SanctionType.WARNING);
        jdbc.update("UPDATE sanction SET status = 'EXPIRED' WHERE id = ?", expired);

        MvcTestResult liftedAgain = api.lift(lifted, "REVOKE");
        MvcTestResult liftExpired = api.lift(expired, "REVOKE");

        assertThat(liftedAgain).hasStatus(HttpStatus.CONFLICT);
        assertThat(liftedAgain).bodyJson().extractingPath("$.code").isEqualTo("SANCTION_INVALID_STATE");
        assertThat(liftExpired).hasStatus(HttpStatus.CONFLICT);
        assertThat(api.sanctionStatus(lifted)).isEqualTo("REVOKED");
        assertThat(api.count("admin_audit_log WHERE action = 'SANCTION_LIFT'")).isEqualTo(1);
    }

    private long confirm(SanctionType type) {
        return sanctionFixture.confirm(api.targetId, api.adminId, type, "사유").sanctionId();
    }
}

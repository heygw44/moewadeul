package com.moewadeul.basecamp.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.basecamp.infra.lifecycle.BasecampSanctionCleanupMapper;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.infra.MemberJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class BasecampSanctionCleanupMapperIntegrationTest {

    @Autowired
    private BasecampSanctionCleanupMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JsonMapper jsonMapper;

    private BasecampAutoTransitionFixture fixture;

    @BeforeEach
    void createFixture() {
        fixture = new BasecampAutoTransitionFixture(jdbc, memberRepository, jsonMapper);
    }

    @Test
    @DisplayName("[F-12][F-13][F-14] 회원이 리더·멤버·신청 대기로 걸린 진행 중 베이스캠프를 id 순으로 돌려준다")
    void returnsInProgressBasecampsOfLeaderMemberAndApplicant() {
        long recruitingLed = fixture.saveBasecamp("RECRUITING");
        long leader = fixture.leaderIdOf(recruitingLed);
        long closedJoined = fixture.saveBasecamp("CLOSED");
        fixture.insertMember(closedJoined, leader, "MEMBER", "ACTIVE");
        long confirmedApplied = fixture.saveBasecamp("CONFIRMED");
        insertApplication(confirmedApplied, leader, "PENDING");

        assertThat(mapper.selectInProgressBasecampIds(leader))
                .containsExactly(recruitingLed, closedJoined, confirmedApplied);
    }

    @Test
    @DisplayName("[F-12][F-13][F-14] 끝났거나 취소된 베이스캠프, 나간 멤버, 대기가 아닌 신청, 다른 회원의 것은 돌려주지 않는다")
    void skipsFinishedLeftAndOthers() {
        long recruitingLed = fixture.saveBasecamp("RECRUITING");
        long member = fixture.leaderIdOf(recruitingLed);
        long completed = fixture.saveBasecamp("COMPLETED");
        fixture.insertMember(completed, member, "MEMBER", "ACTIVE");
        long canceled = fixture.saveBasecamp("CANCELED");
        insertApplication(canceled, member, "PENDING");
        long leftFrom = fixture.saveBasecamp("RECRUITING");
        fixture.insertMember(leftFrom, member, "MEMBER", "LEFT");
        long rejectedAt = fixture.saveBasecamp("RECRUITING");
        insertApplication(rejectedAt, member, "REJECTED");
        long othersOnly = fixture.saveBasecamp("RECRUITING");

        assertThat(mapper.selectInProgressBasecampIds(member)).containsExactly(recruitingLed);
        assertThat(mapper.selectInProgressBasecampIds(fixture.leaderIdOf(othersOnly)))
                .containsExactly(othersOnly);
    }

    @Test
    @DisplayName("[F-12][F-13][F-14] 같은 베이스캠프에 멤버이면서 신청 행도 있으면 한 번만 돌려준다")
    void returnsBasecampOnceWhenMemberAndApplicant() {
        long basecamp = fixture.saveBasecamp("RECRUITING");
        long leader = fixture.leaderIdOf(basecamp);
        insertApplication(basecamp, leader, "PENDING");

        assertThat(mapper.selectInProgressBasecampIds(leader)).containsExactly(basecamp);
    }

    private void insertApplication(long basecampId, long applicantId, String status) {
        jdbc.update(
                "INSERT INTO basecamp_application (basecamp_id, applicant_id, status, applied_at, created_at,"
                        + " updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                basecampId,
                applicantId,
                status,
                BasecampAutoTransitionFixture.AT_DEFAULT_INSTANT,
                BasecampAutoTransitionFixture.AT_DEFAULT_INSTANT,
                BasecampAutoTransitionFixture.AT_DEFAULT_INSTANT);
    }
}

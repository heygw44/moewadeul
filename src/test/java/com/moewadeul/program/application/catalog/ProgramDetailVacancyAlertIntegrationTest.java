package com.moewadeul.program.application.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.program.application.enrollment.ProgramVacancyAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramDetailVacancyAlertIntegrationTest {

    @Autowired
    private ProgramQueryService programQueryService;

    @Autowired
    private ProgramVacancyAlertService vacancyAlertService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private ProgramApplyFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[F-17][PG-07] 상세는 로그인한 회원이 빈자리 알림을 신청했으면 true, 신청하지 않았으면 false이고 비로그인이면 null이다")
    void detailReportsVacancyAlertSubscription() {
        long programId = fixture.saveProgram(2, false);
        fixture.fillSeats(programId, 2);
        long subscriber = fixture.saveMember();
        long other = fixture.saveMember();

        assertThat(programQueryService.detail(programId, subscriber).vacancyAlertSubscribed())
                .isFalse();

        vacancyAlertService.subscribe(subscriber, programId);

        assertThat(programQueryService.detail(programId, subscriber).vacancyAlertSubscribed())
                .isTrue();
        assertThat(programQueryService.detail(programId, other).vacancyAlertSubscribed())
                .isFalse();
        assertThat(programQueryService.detail(programId, null).vacancyAlertSubscribed())
                .isNull();

        vacancyAlertService.unsubscribe(subscriber, programId);

        assertThat(programQueryService.detail(programId, subscriber).vacancyAlertSubscribed())
                .isFalse();
    }
}

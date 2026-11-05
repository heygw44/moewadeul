package com.moewadeul.member.domain;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class MemberPartialUpdateIntegrationTest {

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[SN-04] 잠그지 않고 읽은 회원의 닉네임을 고쳐도 그 사이 다른 트랜잭션이 정한 정지 상태를 덮어쓰지 않는다")
    void unlockedNicknameChangeKeepsConcurrentSuspension() {
        long memberId = memberRepository
                .save(aMember().email(TestSequence.email()).build())
                .getId();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Member stale = memberRepository.findById(memberId).orElseThrow();
            jdbc.update(
                    "UPDATE member SET status = 'SUSPENDED', suspended_until = NOW(6) + INTERVAL 1 DAY WHERE id = ?",
                    memberId);
            stale.changeNickname(TestSequence.nickname(), Instant.now());
        });

        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, memberId))
                .isEqualTo("SUSPENDED");
    }
}

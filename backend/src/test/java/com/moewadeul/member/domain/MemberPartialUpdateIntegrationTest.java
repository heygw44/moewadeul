package com.moewadeul.member.domain;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.TestSequence;
import com.moewadeul.member.infra.MemberJpaRepository;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
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

        String nickname = TestSequence.nickname();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Member stale = memberRepository.findById(memberId).orElseThrow();
            CompletableFuture.runAsync(() -> jdbc.update(
                            "UPDATE member SET status = 'SUSPENDED', suspended_until = NOW(6) + INTERVAL 1 DAY"
                                    + " WHERE id = ?",
                            memberId))
                    .join();
            stale.changeNickname(nickname, Instant.now());
        });

        assertThat(jdbc.queryForMap("SELECT status, nickname FROM member WHERE id = ?", memberId))
                .containsEntry("status", "SUSPENDED")
                .containsEntry("nickname", nickname);
    }
}

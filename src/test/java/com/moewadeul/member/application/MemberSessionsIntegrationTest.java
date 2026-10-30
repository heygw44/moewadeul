package com.moewadeul.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.security.LoginSessionManager;
import com.moewadeul.common.testsupport.IntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class MemberSessionsIntegrationTest {

    private static final long MEMBER_ID = 1L;
    private static final long OTHER_MEMBER_ID = 987_654L;

    @Autowired
    private MemberSessions memberSessions;

    @Autowired
    private JdbcIndexedSessionRepository sessionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("트랜잭션 안에서 회원 세션을 지우려 하면 커넥션을 더 잡기 전에 거부한다")
    void invalidateAll_insideTransaction_isRejected() {
        // when & then
        assertThatThrownBy(() ->
                        transactionTemplate.executeWithoutResult(status -> memberSessions.invalidateAll(MEMBER_ID)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("트랜잭션 밖에서는 회원 세션을 지운다")
    void invalidateAll_outsideTransaction_succeeds() {
        // when & then
        assertThatCode(() -> memberSessions.invalidateAll(MEMBER_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("기준 시각보다 먼저 로그인한 세션과 로그인 시각이 없는 세션만 지우고, 기준 시각과 같거나 뒤인 세션은 남긴다")
    void invalidateLoggedInBefore_deletesOnlySessionsLoggedInBeforeCutoff() {
        // given
        Instant cutoff = Instant.parse("2026-10-05T03:00:00Z");
        String before = saveSession(OTHER_MEMBER_ID, cutoff.minusMillis(1));
        String untouched = saveSession(OTHER_MEMBER_ID, null);
        String atCutoff = saveSession(OTHER_MEMBER_ID, cutoff);
        String after = saveSession(OTHER_MEMBER_ID, cutoff.plusSeconds(1));

        // when
        memberSessions.invalidateLoggedInBefore(OTHER_MEMBER_ID, cutoff);

        // then
        assertThat(sessionRepository.findByPrincipalName(String.valueOf(OTHER_MEMBER_ID)))
                .containsOnlyKeys(atCutoff, after)
                .doesNotContainKeys(before, untouched);
        memberSessions.invalidateAll(OTHER_MEMBER_ID);
    }

    private String saveSession(long memberId, Instant loggedInAt) {
        return saveSession(sessionRepository, memberId, loggedInAt);
    }

    private static <S extends Session> String saveSession(
            FindByIndexNameSessionRepository<S> repository, long memberId, Instant loggedInAt) {
        S session = repository.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, String.valueOf(memberId));
        if (loggedInAt != null) {
            session.setAttribute(LoginSessionManager.LOGIN_INSTANT_ATTRIBUTE, loggedInAt);
        }
        repository.save(session);
        return session.getId();
    }
}

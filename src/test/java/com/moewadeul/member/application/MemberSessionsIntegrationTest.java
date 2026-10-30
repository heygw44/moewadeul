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
    @DisplayName("회원의 현재 비밀번호 변경 시각과 다른 값을 적은 세션과 값이 없는 세션만 지우고, 같은 값의 세션은 남긴다")
    void invalidateNotLoggedInWith_deletesOnlySessionsWithAnotherPasswordSnapshot() {
        // given
        Instant changedAt = Instant.parse("2026-10-05T03:00:00Z");
        String older =
                saveSession(OTHER_MEMBER_ID, LoginSessionManager.passwordChangedAtSnapshot(changedAt.minusMillis(1)));
        String neverChanged = saveSession(OTHER_MEMBER_ID, LoginSessionManager.passwordChangedAtSnapshot(null));
        String untouched = saveSession(OTHER_MEMBER_ID, null);
        String current = saveSession(OTHER_MEMBER_ID, LoginSessionManager.passwordChangedAtSnapshot(changedAt));

        // when
        memberSessions.invalidateNotLoggedInWith(OTHER_MEMBER_ID, changedAt);

        // then
        assertThat(sessionRepository.findByPrincipalName(String.valueOf(OTHER_MEMBER_ID)))
                .containsOnlyKeys(current)
                .doesNotContainKeys(older, neverChanged, untouched);
        memberSessions.invalidateAll(OTHER_MEMBER_ID);
    }

    @Test
    @DisplayName("비밀번호를 바꾼 적 없는 회원은 null을 적은 세션만 남기고, 속성이 없는 세션은 지운다")
    void invalidateNotLoggedInWith_nullPasswordChangedAt_keepsOnlyNullSnapshot() {
        // given
        String neverChanged = saveSession(OTHER_MEMBER_ID, LoginSessionManager.passwordChangedAtSnapshot(null));
        String untouched = saveSession(OTHER_MEMBER_ID, null);

        // when
        memberSessions.invalidateNotLoggedInWith(OTHER_MEMBER_ID, null);

        // then
        assertThat(sessionRepository.findByPrincipalName(String.valueOf(OTHER_MEMBER_ID)))
                .containsOnlyKeys(neverChanged)
                .doesNotContainKeys(untouched);
        memberSessions.invalidateAll(OTHER_MEMBER_ID);
    }

    private String saveSession(long memberId, String passwordSnapshot) {
        return saveSession(sessionRepository, memberId, passwordSnapshot);
    }

    private static <S extends Session> String saveSession(
            FindByIndexNameSessionRepository<S> repository, long memberId, String passwordSnapshot) {
        S session = repository.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, String.valueOf(memberId));
        if (passwordSnapshot != null) {
            session.setAttribute(LoginSessionManager.PASSWORD_CHANGED_AT_ATTRIBUTE, passwordSnapshot);
        }
        repository.save(session);
        return session.getId();
    }
}

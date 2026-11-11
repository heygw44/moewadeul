package com.moewadeul.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.security.ThrottledSessionRepository.ThrottledSession;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;

class ThrottledSessionRepositoryTest {

    private final ThrottledSessionRepository<MapSession> repository =
            new ThrottledSessionRepository<>(new MapSessionRepository(new ConcurrentHashMap<>()));

    @AfterEach
    void closeMemo() {
        repository.closeRequestMemo();
    }

    @Test
    @DisplayName("[F-02] 저장된 마지막 접근 시각에서 60초가 안 지났으면 접근 시각을 바꾸지 않는다")
    void lastAccessWithinIntervalIsIgnored() {
        // given
        ThrottledSession<MapSession> session = repository.createSession();
        Instant stored = session.getLastAccessedTime();

        // when
        session.setLastAccessedTime(stored.plus(Duration.ofSeconds(59)));

        // then
        assertThat(session.getLastAccessedTime()).isEqualTo(stored);
    }

    @Test
    @DisplayName("[F-02] 저장된 마지막 접근 시각에서 60초가 지났으면 접근 시각을 바꾼다")
    void lastAccessAfterIntervalIsWritten() {
        // given
        ThrottledSession<MapSession> session = repository.createSession();
        Instant later = session.getLastAccessedTime().plus(Duration.ofSeconds(60));

        // when
        session.setLastAccessedTime(later);

        // then
        assertThat(session.getLastAccessedTime()).isEqualTo(later);
    }

    @Test
    @DisplayName("[F-02] 한 요청 안에서 지운 세션은 다시 찾아도 없다")
    void deletedSessionIsNotServedFromMemo() {
        // given
        String id = savedSessionId();
        repository.openRequestMemo();
        repository.findById(id);

        // when
        repository.deleteById(id);

        // then
        assertThat(repository.findById(id)).isNull();
    }

    @Test
    @DisplayName("[F-02] 한 요청 안에서 세션 ID를 바꾸면 옛 ID로 다시 찾아도 없다")
    void renamedSessionIsNotServedUnderOldId() {
        // given
        String oldId = savedSessionId();
        repository.openRequestMemo();
        ThrottledSession<MapSession> session = repository.findById(oldId);

        // when
        session.changeSessionId();
        repository.save(session);

        // then
        assertThat(repository.findById(oldId)).isNull();
    }

    @Test
    @DisplayName("[F-02] 한 요청 안에서 같은 세션을 다시 찾으면 처음 읽은 세션을 돌려준다")
    void sameSessionIsReadOncePerRequest() {
        // given
        String id = savedSessionId();
        repository.openRequestMemo();
        ThrottledSession<MapSession> first = repository.findById(id);

        // when
        ThrottledSession<MapSession> second = repository.findById(id);

        // then
        assertThat(second).isSameAs(first);
    }

    private String savedSessionId() {
        ThrottledSession<MapSession> session = repository.createSession();
        repository.save(session);
        return session.getId();
    }
}

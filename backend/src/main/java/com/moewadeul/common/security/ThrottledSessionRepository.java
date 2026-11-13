package com.moewadeul.common.security;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

// 응답이 일찍 커밋되면 Spring Session은 커밋 때 요청 세션 캐시를 비우고 세션을 다시 읽는다. 그 재조회를 요청 메모로 막는다.
final class ThrottledSessionRepository<S extends Session>
        implements SessionRepository<ThrottledSessionRepository.ThrottledSession<S>> {

    private static final Duration LAST_ACCESS_WRITE_INTERVAL = Duration.ofSeconds(60);

    private final SessionRepository<S> delegate;
    private final ThreadLocal<Map<String, ThrottledSession<S>>> requestMemo = new ThreadLocal<>();

    ThrottledSessionRepository(SessionRepository<S> delegate) {
        this.delegate = delegate;
    }

    void openRequestMemo() {
        requestMemo.set(new HashMap<>());
    }

    void closeRequestMemo() {
        requestMemo.remove();
    }

    @Override
    public ThrottledSession<S> createSession() {
        return new ThrottledSession<>(delegate.createSession());
    }

    @Override
    public void save(ThrottledSession<S> session) {
        delegate.save(session.inner);
    }

    @Override
    public @Nullable ThrottledSession<S> findById(String id) {
        Map<String, ThrottledSession<S>> memo = requestMemo.get();
        if (memo != null) {
            ThrottledSession<S> remembered = memo.get(id);
            if (remembered != null && remembered.getId().equals(id)) {
                return remembered;
            }
        }
        S found = delegate.findById(id);
        if (found == null) {
            return null;
        }
        ThrottledSession<S> wrapped = new ThrottledSession<>(found);
        if (memo != null) {
            memo.put(id, wrapped);
        }
        return wrapped;
    }

    @Override
    public void deleteById(String id) {
        Map<String, ThrottledSession<S>> memo = requestMemo.get();
        if (memo != null) {
            memo.remove(id);
        }
        delegate.deleteById(id);
    }

    static final class ThrottledSession<S extends Session> implements Session {

        private final S inner;

        private ThrottledSession(S inner) {
            this.inner = inner;
        }

        @Override
        public String getId() {
            return inner.getId();
        }

        @Override
        public String changeSessionId() {
            return inner.changeSessionId();
        }

        @Override
        public <T> @Nullable T getAttribute(String attributeName) {
            return inner.getAttribute(attributeName);
        }

        @Override
        public Set<String> getAttributeNames() {
            return inner.getAttributeNames();
        }

        @Override
        public void setAttribute(String attributeName, @Nullable Object attributeValue) {
            inner.setAttribute(attributeName, attributeValue);
        }

        @Override
        public void removeAttribute(String attributeName) {
            inner.removeAttribute(attributeName);
        }

        @Override
        public Instant getCreationTime() {
            return inner.getCreationTime();
        }

        @Override
        public void setLastAccessedTime(Instant lastAccessedTime) {
            if (storedAccessIsStale(lastAccessedTime)) {
                inner.setLastAccessedTime(lastAccessedTime);
            }
        }

        private boolean storedAccessIsStale(Instant now) {
            return Duration.between(inner.getLastAccessedTime(), now).compareTo(LAST_ACCESS_WRITE_INTERVAL) >= 0;
        }

        @Override
        public Instant getLastAccessedTime() {
            return inner.getLastAccessedTime();
        }

        @Override
        public void setMaxInactiveInterval(Duration interval) {
            inner.setMaxInactiveInterval(interval);
        }

        @Override
        public Duration getMaxInactiveInterval() {
            return inner.getMaxInactiveInterval();
        }

        @Override
        public boolean isExpired() {
            return inner.isExpired();
        }
    }
}

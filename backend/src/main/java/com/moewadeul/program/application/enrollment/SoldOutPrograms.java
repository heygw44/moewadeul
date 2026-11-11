package com.moewadeul.program.application.enrollment;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

// 앱 인스턴스 하나를 전제한다. 다른 인스턴스에서 자리를 얻은 회원은 이 인스턴스의 표시에 더해지지 않아 TTL 동안 거절될 수 있다.
// 취소·만료로 빈 자리와 관리자 변경은 최대 TTL만큼 늦게 보인다.
@Component
class SoldOutPrograms {

    static final Duration TTL = Duration.ofSeconds(1);

    private static final int ADMISSION_STRIPES = 64;

    record SeatHolders(long programId, List<Long> memberIds, long admissionsBeforeRead) {}

    private record Mark(Instant until, Set<Long> seatHolders) {

        Mark withSeatHolder(long memberId) {
            Set<Long> holders = new HashSet<>(seatHolders);
            holders.add(memberId);
            return new Mark(until, Set.copyOf(holders));
        }
    }

    private record Seat(long programId, long memberId) {}

    private final ConcurrentHashMap<Long, Mark> marks = new ConcurrentHashMap<>();
    private final Set<Seat> committing = ConcurrentHashMap.newKeySet();
    private final AtomicLongArray admissions = new AtomicLongArray(ADMISSION_STRIPES);

    // 읽는 사이 커밋된 신청이 있으면 읽은 목록에 그 회원이 빠졌을 수 있다. 그래서 읽기 전 커밋 수를 함께 넘겨 mark가 비교하게 한다.
    SeatHolders read(long programId, Supplier<List<Long>> reader) {
        long before = admissions.get(stripe(programId));
        return new SeatHolders(programId, reader.get(), before);
    }

    void mark(SeatHolders holders, Instant applyCloseAt, Instant now) {
        marks.values().removeIf(mark -> !now.isBefore(mark.until()));
        Instant ttlEnd = now.plus(TTL);
        Mark fresh = new Mark(applyCloseAt.isBefore(ttlEnd) ? applyCloseAt : ttlEnd, Set.copyOf(holders.memberIds()));
        int stripe = stripe(holders.programId());
        marks.compute(
                holders.programId(),
                (id, old) -> admissions.get(stripe) == holders.admissionsBeforeRead() ? fresh : old);
    }

    // 커밋 직전부터 표시에 더해질 때까지 committing에 두어, 커밋된 자리를 쥔 회원이 거절되는 틈을 없앤다.
    void beforeCommit(long programId, long memberId) {
        committing.add(new Seat(programId, memberId));
    }

    // 커밋 여부를 모르면 커밋된 것으로 본다. 자리를 쥔 회원을 더 넣는 쪽은 그 회원이 원래 경로로 갈 뿐이라 안전하다.
    void afterCompletion(long programId, long memberId, boolean mayHaveCommitted) {
        if (mayHaveCommitted) {
            admissions.incrementAndGet(stripe(programId));
            marks.computeIfPresent(programId, (id, mark) -> mark.withSeatHolder(memberId));
        }
        committing.remove(new Seat(programId, memberId));
    }

    // afterCompletion이 표시에 더한 뒤 committing에서 빼므로, committing을 먼저 봐야 그 사이에 온 요청도 놓치지 않는다.
    boolean rejects(long programId, long memberId, Instant now) {
        if (committing.contains(new Seat(programId, memberId))) {
            return false;
        }
        Mark mark = marks.get(programId);
        if (mark == null) {
            return false;
        }
        if (!now.isBefore(mark.until())) {
            marks.remove(programId, mark);
            return false;
        }
        return !mark.seatHolders().contains(memberId);
    }

    void clear() {
        marks.clear();
        committing.clear();
    }

    private static int stripe(long programId) {
        return Math.floorMod(Long.hashCode(programId), ADMISSION_STRIPES);
    }
}

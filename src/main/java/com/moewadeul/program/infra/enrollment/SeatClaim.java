package com.moewadeul.program.infra.enrollment;

import java.time.Instant;
import lombok.Getter;

// MyBatis가 useGeneratedKeys로 만든 ID를 id 필드에 채워야 해서 record가 아니라 클래스다.
@Getter
public class SeatClaim {

    private final long programId;
    private final long memberId;
    private final Instant paymentDueAt;
    private final Instant now;
    private Long id;

    public SeatClaim(long programId, long memberId, Instant paymentDueAt, Instant now) {
        this.programId = programId;
        this.memberId = memberId;
        this.paymentDueAt = paymentDueAt;
        this.now = now;
    }
}

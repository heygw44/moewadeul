package com.moewadeul.member.infra.verification;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 행이 없으면 만들고 있으면 잠그는 일을 문장 하나로 하려고 MyBatis를 쓴다. JPA로 먼저 조회하고 저장하면 같은 IP의 첫 요청 둘이
// 모두 행이 없다고 보고 둘 다 넣으려다 한쪽이 기본 키 충돌로 실패한다. 삭제도 지울 행 수를 제한하는 문장이라 같은 이유로 여기에 둔다.
@Mapper
public interface EmailVerificationIpLockMapper {

    /**
     * 호출하면 그 IP의 행이 없을 때 만들고, 있으면 마지막 요청 시각만 갱신한다. 어느 쪽이든 그 행을 쓰기 잠금으로 잡으므로,
     * 같은 IP로 이 문장을 부른 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 기다린다. 트랜잭션 안에서만 호출한다.
     */
    int lock(@Param("requestIp") String requestIp, @Param("now") Instant now);

    /** 호출하면 마지막 요청이 cutoff보다 먼저인 행을 최대 limit개 지우고, 지운 행 수를 돌려준다. */
    int deleteIdleBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}

package com.moewadeul.aisearch.infra;

import com.moewadeul.aisearch.application.TokenTotals;
import java.time.Instant;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 행을 "없을 때만 넣기"와 "횟수·토큰 더하기"는 조건부 문장이라 MyBatis를 쓴다. JPA로 조회하고 저장하면
// 같은 회원의 첫 요청 둘이 모두 행이 없다고 보고 둘 다 넣으려다 한쪽이 기본 키 충돌로 실패한다.
// 하루 합계와 지울 행 수를 제한하는 삭제도 같은 이유로 여기에 둔다.
@Mapper
public interface AiSearchUsageMapper {

    /**
     * 호출하면 그 회원·날짜의 행이 없을 때 요청 0번인 행을 만든다. 이미 있으면 아무것도 바꾸지 않는다.
     * 같은 회원이 동시에 호출해도 기본 키 충돌 없이 모두 성공한다.
     */
    int insertIfAbsent(@Param("memberId") long memberId, @Param("date") LocalDate date, @Param("now") Instant now);

    /** 호출하면 행을 잠그고 지금까지 센 요청 횟수를 돌려준다. insertIfAbsent로 행을 먼저 만들어 둔 트랜잭션에서 부른다. */
    int selectRequestCountForUpdate(@Param("memberId") long memberId, @Param("date") LocalDate date);

    int incrementRequestCount(
            @Param("memberId") long memberId, @Param("date") LocalDate date, @Param("now") Instant now);

    int addTokens(
            @Param("memberId") long memberId,
            @Param("date") LocalDate date,
            @Param("inputTokens") int inputTokens,
            @Param("outputTokens") int outputTokens,
            @Param("now") Instant now);

    /** 호출하면 그 날짜에 모든 회원이 쓴 토큰의 합계를 돌려준다. 행이 없으면 0이다. */
    TokenTotals sumTokensOn(@Param("date") LocalDate date);

    /** 호출하면 날짜가 cutoff보다 앞선 행을 최대 limit개 지우고, 지운 행 수를 돌려준다. */
    int deleteBefore(@Param("cutoff") LocalDate cutoff, @Param("limit") int limit);
}

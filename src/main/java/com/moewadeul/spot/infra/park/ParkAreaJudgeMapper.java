package com.moewadeul.spot.infra.park;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 공원 경계 판정은 MySQL의 공간 함수 ST_Contains로 점이 경계 안에 있는지 계산한다. JPA로는 이 함수를 쓰는 조회와
// 판정 결과의 일괄 갱신을 표현하기 어려워서, 이 매퍼가 SQL을 직접 쓴다.
// 이 매퍼의 메서드는 호출하는 쪽(ParkAreaJudgeService)이 연 트랜잭션 안에서 실행해야 한다.
@Mapper
public interface ParkAreaJudgeMapper {

    /** 호출하면 좌표를 포함하는 공원 경계의 ID를 돌려준다. 여러 경계가 겹치면 가장 작은 ID를, 포함하는 경계가 없으면 null을 돌려준다. */
    Long selectContainingAreaId(@Param("lat") double latitude, @Param("lng") double longitude);

    /** 호출하면 id인 공원 경계의 이름, 출처, 기준일을 돌려준다. 그런 경계가 없으면 null을 돌려준다. */
    ProtectedAreaEvidenceRow selectAreaEvidence(@Param("id") long id);

    /**
     * 호출하면 상태와 상관없이 ID가 afterId보다 큰 박지를 ID 순서로 최대 limit건 읽어 ID, 좌표, 판정 전 공원 경고 여부, 낙관적 잠금 번호를 돌려준다.
     * 잠그지 않는다. 남은 박지가 없으면 빈 목록을 돌려준다.
     */
    List<BakjiPointRow> selectBakjiPoints(@Param("afterId") long afterId, @Param("limit") int limit);

    /**
     * 호출하면 ids인 박지 행을 ID 순서로 쓰기 잠금하며 읽어 지금의 좌표, 공원 경고 여부, 낙관적 잠금 번호를 돌려준다. 잠금 읽기라서 트랜잭션의 스냅숏이 아니라
     * 마지막으로 커밋된 값을 읽는다. ids는 비어 있으면 안 된다.
     */
    List<BakjiPointRow> selectBakjiPointsForUpdate(@Param("ids") List<Long> ids);

    /**
     * 호출하면 targets의 박지 가운데 낙관적 잠금 번호가 읽은 때와 같은 행에만 판정 결과를 기록하고 번호를 올린다. protectedAreaId가 null이면 경계 밖으로 보고
     * 경고를 끄며, 아니면 경고를 켜고 그 경계를 근거로 남긴다. area_checked_at은 now로 기록하고 updated_at은 건드리지 않는다. targets는 비어 있으면 안
     * 된다. 갱신한 행 수를 돌려준다. 그 사이 다른 쓰기가 바꾼 행은 갱신하지 않으므로, 돌려준 수가 targets 수보다 작을 수 있다.
     */
    int updateJudgement(
            @Param("targets") List<BakjiJudgeTarget> targets,
            @Param("protectedAreaId") Long protectedAreaId,
            @Param("now") Instant now);
}

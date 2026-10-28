package com.moewadeul.spot.infra.park;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ParkAreaJudgeMapper {

    Long selectContainingAreaId(@Param("lat") double latitude, @Param("lng") double longitude);

    ProtectedAreaEvidenceRow selectAreaEvidence(@Param("id") long id);

    List<BakjiPointRow> selectBakjiPoints(@Param("afterId") long afterId, @Param("limit") int limit);

    List<BakjiPointRow> selectBakjiPointsForUpdate(@Param("ids") List<Long> ids);

    int updateJudgement(
            @Param("targets") List<BakjiJudgeTarget> targets,
            @Param("protectedAreaId") Long protectedAreaId,
            @Param("now") Instant now);
}

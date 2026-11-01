package com.moewadeul.program.infra.enrollment;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProgramApplicationMapper {

    int insertIfSeatAvailable(SeatClaim claim);

    long countSeatHolders(@Param("programId") long programId, @Param("now") Instant now);

    boolean existsActive(@Param("programId") long programId, @Param("memberId") long memberId);
}

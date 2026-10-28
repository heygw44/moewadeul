package com.moewadeul.program.infra.enrollment;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProgramApplicationMapper {

    int insertIfSeatAvailable(SeatClaim claim);

    boolean existsActive(@Param("programId") long programId, @Param("memberId") long memberId);
}

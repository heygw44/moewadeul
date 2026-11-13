package com.moewadeul.program.infra.enrollment;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProgramApplicationMapper {

    int insertIfSeatAvailable(SeatClaim claim);

    long countSeatHolders(@Param("programId") long programId, @Param("now") Instant now);

    List<Long> selectSeatHolderIds(@Param("programId") long programId, @Param("now") Instant now);

    boolean existsActive(@Param("programId") long programId, @Param("memberId") long memberId);
}

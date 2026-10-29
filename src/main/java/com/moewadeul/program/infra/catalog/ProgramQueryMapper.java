package com.moewadeul.program.infra.catalog;

import com.moewadeul.program.domain.ProgramApplicationStatus;
import com.moewadeul.program.domain.ProgramPhase;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProgramQueryMapper {

    List<ProgramListRow> selectList(
            @Param("phase") ProgramPhase phase,
            @Param("now") Instant now,
            @Param("offset") long offset,
            @Param("limit") int limit);

    Instant selectNextApplyOpenAt(@Param("now") Instant now);

    ProgramDetailRow selectDetail(@Param("programId") long programId, @Param("now") Instant now);

    MyApplicationRow selectLatestApplication(@Param("programId") long programId, @Param("memberId") long memberId);

    List<ProgramApplicantRow> selectApplicants(
            @Param("programId") long programId,
            @Param("status") ProgramApplicationStatus status,
            @Param("offset") long offset,
            @Param("limit") int limit);

    List<MyProgramApplicationRow> selectMyApplications(
            @Param("memberId") long memberId,
            @Param("status") ProgramApplicationStatus status,
            @Param("offset") long offset,
            @Param("limit") int limit);

    boolean existsActiveSpot(@Param("spotId") long spotId);
}

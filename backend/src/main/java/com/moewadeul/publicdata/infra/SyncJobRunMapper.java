package com.moewadeul.publicdata.infra;

import com.moewadeul.publicdata.domain.SyncJobType;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SyncJobRunMapper {

    List<SyncJobRunRow> selectRuns(
            @Param("jobType") SyncJobType jobType, @Param("offset") long offset, @Param("limit") int limit);
}

package com.moewadeul.basecamp.infra.lifecycle;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BasecampContactCleanupMapper {

    int clearContactInfo(@Param("completedBefore") Instant completedBefore, @Param("now") Instant now);
}

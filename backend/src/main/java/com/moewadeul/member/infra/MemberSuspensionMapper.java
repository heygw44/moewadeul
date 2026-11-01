package com.moewadeul.member.infra;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberSuspensionMapper {

    int releaseExpired(@Param("now") Instant now);
}

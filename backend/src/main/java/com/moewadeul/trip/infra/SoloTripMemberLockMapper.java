package com.moewadeul.trip.infra;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SoloTripMemberLockMapper {

    Long lockMember(@Param("memberId") long memberId);
}

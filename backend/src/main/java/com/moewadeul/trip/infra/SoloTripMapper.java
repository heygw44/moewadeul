package com.moewadeul.trip.infra;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SoloTripMapper {

    List<SoloTripRow> selectUpcomingByMember(@Param("memberId") long memberId, @Param("today") LocalDate today);
}

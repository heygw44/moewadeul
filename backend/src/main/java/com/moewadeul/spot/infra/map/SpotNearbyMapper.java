package com.moewadeul.spot.infra.map;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SpotNearbyMapper {

    long countNearbyUpTo(@Param("nearby") SpotNearbyCondition nearby, @Param("limit") long limit);

    List<SpotNearbyRow> selectNearby(
            @Param("nearby") SpotNearbyCondition nearby, @Param("offset") long offset, @Param("limit") int limit);
}

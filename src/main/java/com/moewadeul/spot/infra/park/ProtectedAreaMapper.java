package com.moewadeul.spot.infra.park;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProtectedAreaMapper {

    List<ProtectedAreaKeyRow> selectKeysBySource(@Param("source") String source);

    int insert(@Param("area") ProtectedAreaInsertRow area, @Param("now") Instant now);

    int updateBoundary(@Param("area") ProtectedAreaUpdateRow area, @Param("now") Instant now);
}

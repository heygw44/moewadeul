package com.moewadeul.spot.infra.map;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SpotAreaMapper {

    List<SpotMarkerRow> selectMarkers(@Param("area") SpotAreaCondition area, @Param("limit") int limit);

    long countInsideCells(@Param("area") SpotAreaCondition area, @Param("cells") SpotGridCells cells);

    long countClosedInArea(@Param("area") SpotAreaCondition area);

    List<SpotClusterRow> selectSettingClusters(
            @Param("area") SpotAreaCondition area, @Param("grid") SpotClusterGrid grid);

    List<SpotClusterRow> selectClusters(
            @Param("area") SpotAreaCondition area,
            @Param("grid") SpotClusterGrid grid,
            @Param("cells") SpotGridCells cells);

    List<SpotClusterRow> selectGridClusters(@Param("area") SpotAreaCondition area, @Param("cells") SpotGridCells cells);
}

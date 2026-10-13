package com.moewadeul.spot.infra;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 지도 영역 조회는 위도·경도 범위 조건에 유형, 시설, 경고 필터를 요청마다 골라 붙이고, 장소가 많으면 미리 세어 둔 격자 칸을 화면 칸으로 합친다.
// JPA로는 동적 조건과 필터별 조인, 인덱스 힌트, 집계 표 선택을 한 문장에 담기 어렵다. 그래서 이 매퍼가 SQL로 직접 쓴다.
// 결과는 조회 전용 record로만 받고, 엔티티는 만들지 않는다.
@Mapper
public interface SpotAreaMapper {

    /**
     * 호출하면 area 안의 ACTIVE 장소를 ID 순서로 최대 limit건 읽는다. 공공데이터 장소는 운영 상태와 휴장 기간을 함께 읽고, 박지는 그 값들이
     * {@code null}이다.
     */
    List<SpotMarkerRow> selectMarkers(@Param("area") SpotAreaCondition area, @Param("limit") int limit);

    /**
     * 호출하면 area 안에 통째로 든 cells의 격자 칸에서 조건에 맞는 ACTIVE 장소 수를 센다. 휴장 제외는 반영하지 않는다. 이 칸의 장소는 모두 영역 안이라서,
     * 결과는 영역 안 장소 수보다 크지 않다.
     */
    long countInsideCells(@Param("area") SpotAreaCondition area, @Param("cells") SpotGridCells cells);

    /** 호출하면 area 안에서 유형·경고 조건에 맞고 오늘 휴장인 공공 장소 수를 정확히 센다. */
    long countClosedInArea(@Param("area") SpotAreaCondition area);

    /**
     * 호출하면 area 안의 ACTIVE 장소 중 조건에 맞는 장소를 격자 집계 표 없이 직접 세어 grid의 화면 칸별 평균 좌표와 장소 수로 읽는다. 입지 필터처럼
     * 집계 표에 없는 조건이 있을 때 쓴다. 장소 자기 좌표로 화면 칸을 정하고, 휴장 제외도 반영한다. 장소가 없는 칸은 결과에 나오지 않고, 결과는 남쪽
     * 행부터, 같은 행에서는 서쪽 칸부터 나온다.
     */
    List<SpotClusterRow> selectSettingClusters(
            @Param("area") SpotAreaCondition area, @Param("grid") SpotClusterGrid grid);

    /**
     * 호출하면 미리 세어 둔 cells의 격자 칸을 grid의 화면 칸으로 합쳐 칸마다 평균 좌표와 장소 수를 읽는다. 격자 칸의 장소는 모두 그 격자 칸 중심이 속한
     * 화면 칸에 들어가고, 중심이 영역 밖인 격자 칸은 세지 않는다. 장소가 없는 칸은 결과에 나오지 않고, 결과는 남쪽 행부터, 같은 행에서는 서쪽 칸부터 나온다.
     */
    List<SpotClusterRow> selectClusters(
            @Param("area") SpotAreaCondition area,
            @Param("grid") SpotClusterGrid grid,
            @Param("cells") SpotGridCells cells);
}

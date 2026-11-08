package com.moewadeul.spot.infra.map;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.testsupport.IntegrationTest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotMarkerCoveringIndexIntegrationTest {

    private static final int GRID = 40;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    @DisplayName("[NFR-01] 마커 조회는 위도·경도 인덱스만 읽고 장소 본 행을 다시 읽지 않는다")
    void markerQueryReadsOnlyLatitudeLongitudeIndex() {
        // given
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                double lat = 34.0 + i * 0.1;
                double lng = 126.0 + j * 0.08;
                rows.add(new Object[] {"마커" + i + "-" + j, "POINT(" + lat + " " + lng + ")"});
            }
        }
        jdbc.batchUpdate(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, park_warning, status, created_at,"
                        + " updated_at) VALUES ('BAKJI', ?, ST_GeomFromText(?, 4326), 60, 127, FALSE, 'ACTIVE',"
                        + " NOW(6), NOW(6))",
                rows);
        jdbc.execute("ANALYZE TABLE spot");
        SpotAreaCondition area = new SpotAreaCondition(
                35.0, 126.5, 35.3, 126.8, Set.of(), Set.of(), false, false, false, false, LocalDate.of(2026, 6, 15));

        // when
        Explained spot = explainSpotRow(area);

        // then
        assertThat(spot.key()).isEqualTo("idx_spot_status_latitude_longitude");
        assertThat(spot.extra().split("; ")).contains("Using index");
    }

    private Explained explainSpotRow(SpotAreaCondition area) {
        MappedStatement statement = sqlSessionFactory
                .getConfiguration()
                .getMappedStatement(SpotAreaMapper.class.getName() + ".selectMarkers");
        MapperMethod.ParamMap<Object> parameter = new MapperMethod.ParamMap<>();
        parameter.put("area", area);
        parameter.put("limit", 500);
        BoundSql boundSql = statement.getBoundSql(parameter);
        return jdbc.execute((ConnectionCallback<Explained>) connection -> {
            try (PreparedStatement explain = connection.prepareStatement("EXPLAIN " + boundSql.getSql())) {
                new DefaultParameterHandler(statement, parameter, boundSql).setParameters(explain);
                try (ResultSet rows = explain.executeQuery()) {
                    while (rows.next()) {
                        if ("s".equals(rows.getString("table"))) {
                            return new Explained(rows.getString("key"), rows.getString("Extra"));
                        }
                    }
                    throw new IllegalStateException("실행 계획에 장소 표가 없다");
                }
            }
        });
    }

    private record Explained(String key, String extra) {}
}

package com.moewadeul.publicdata.application;

import com.moewadeul.spot.application.PublicSpotUpsertResult;
import java.time.LocalDate;

/**
 * 전국휴양림표준데이터 파일을 한 번 적재한 결과.
 *
 * @param sourceDate 저장한 휴양림 대표 행의 데이터기준일자 중 가장 늦은 날짜. 읽을 수 있는 날짜가 하나도 없으면 null이다.
 * @param processedCount 파일의 행 수. 건너뛴 행도 센다.
 * @param groupCount 숙박 불가와 잘못된 행을 뺀 뒤 100m 안의 행을 묶어 만든 휴양림 수. 국립과 겹쳐 뺀 휴양림도 센다.
 * @param removed 이번 적재로 새로 숨긴 휴양림 수. 파일에서 빠졌거나 국립과 겹치게 돼서 원천 삭제로 숨긴 휴양림이다.
 * @param skippedNoLodging 숙박가능여부가 {@code Y}가 아니라서 뺀 행 수
 * @param skippedInvalid 이름이 비었거나 위경도를 읽지 못해서 뺀 행 수
 * @param skippedNational 국립자연휴양림과 겹쳐서 뺀 휴양림 수
 * @param rejected 장소 모듈이 거부한 휴양림 수. 예를 들어 좌표가 기상청 격자 밖인 경우다.
 */
public record ForestStandardLoadResult(
        LocalDate sourceDate,
        int processedCount,
        int groupCount,
        int inserted,
        int updated,
        int unchanged,
        int removed,
        int skippedNoLodging,
        int skippedInvalid,
        int skippedNational,
        int rejected) {

    static ForestStandardLoadResult of(
            LocalDate sourceDate,
            int processedCount,
            ForestStandardMatcher.Result matched,
            PublicSpotUpsertResult upserted,
            int removed) {
        return new ForestStandardLoadResult(
                sourceDate,
                processedCount,
                matched.groupCount(),
                upserted.inserted(),
                upserted.updated(),
                upserted.unchanged(),
                removed,
                matched.skippedNoLodging(),
                matched.skippedInvalid(),
                matched.skippedNational(),
                upserted.skipped());
    }

    /** 실행 기록에 남기는 건너뛴 수. 건너뛴 행, 겹쳐서 뺀 휴양림, 장소 모듈이 거부한 휴양림을 합친다. */
    int skipped() {
        return skippedNoLodging + skippedInvalid + skippedNational + rejected;
    }
}

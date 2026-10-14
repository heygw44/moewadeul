package com.moewadeul.publicdata.infra.forest;

import java.util.List;

/**
 * 전국휴양림표준데이터 파일 하나를 읽은 결과.
 *
 * <p>이 파일은 파일 전체의 기준일이 없고 행마다 기준일이 있다. 그래서 기준일은 각 {@link ForestStandardRecord}가 가진다.
 *
 * @param records 파일의 데이터 행을 파일 순서대로 담는다.
 */
public record ForestStandardFile(List<ForestStandardRecord> records) {

    public ForestStandardFile {
        records = List.copyOf(records);
    }
}

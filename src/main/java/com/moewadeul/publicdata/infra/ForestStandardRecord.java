package com.moewadeul.publicdata.infra;

/**
 * 전국휴양림표준데이터 파일의 한 행에서 장소 적재에 쓰는 항목만 담는다. 입장료, 주요시설명처럼 지금 적재하지 않는 항목은 담지 않는다.
 *
 * <p>값은 파일에 적힌 내용을 문자열로 바꾼 그대로다. 파일의 값이 숫자이면 숫자의 글자 표현을, null이면 빈 문자열을 담는다. 그래서 앞뒤 공백 제거,
 * 숫자 변환, 빈 값 처리는 이 값을 쓰는 쪽이 한다.
 *
 * @param name 휴양림명
 * @param province 시도명. 파일에 없어도 되는 항목이라, 키가 없으면 빈 문자열이다.
 * @param lodgingAvailable 숙박가능여부. {@code Y} 또는 {@code N}이다.
 * @param address 소재지도로명주소
 * @param phone 휴양림전화번호
 * @param homepage 홈페이지주소
 * @param latitude 위도
 * @param longitude 경도
 * @param sourceDate 데이터기준일자. 행마다 다르고 {@code yyyy-MM-dd} 형식이다.
 */
public record ForestStandardRecord(
        String name,
        String province,
        String lodgingAvailable,
        String address,
        String phone,
        String homepage,
        String latitude,
        String longitude,
        String sourceDate) {}

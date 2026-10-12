package com.moewadeul.memory.domain;

import java.time.LocalDate;

/**
 * 추억 기록 한 건의 내용. 사진은 따로 붙이므로 여기에 없다.
 *
 * <p>만들 때 행 하나로 판단할 수 있는 규칙을 검사한다. 위도는 -90~90, 경도는 -180~180, 박 수는 {@value #MIN_NIGHTS}~{@value
 * #MAX_NIGHTS}이고, 글은 없어도 되지만 있으면 공백뿐일 수 없고 {@value #CONTENT_MAX_LENGTH}자 이하다. 어기면
 * {@link IllegalArgumentException}을 던진다. 방문일이 오늘보다 뒤인지는 오늘을 아는 호출하는 쪽이 {@link #isVisitedAfter}로 검사한다.
 *
 * @param spotId 연결한 장소. 연결하지 않았으면 null이다
 * @param content 글. 쓰지 않았으면 null이다
 */
public record MemoryDetails(
        Long spotId,
        double latitude,
        double longitude,
        LocalDate visitedDate,
        int nights,
        String content,
        MemoryVisibility visibility) {

    public static final double MAX_LATITUDE = 90;
    public static final double MAX_LONGITUDE = 180;
    public static final int MIN_NIGHTS = 0;
    public static final int MAX_NIGHTS = 30;

    /** 글의 최대 길이다. DB 컬럼 크기와 같다. */
    public static final int CONTENT_MAX_LENGTH = 2000;

    public MemoryDetails {
        if (visitedDate == null || visibility == null) {
            throw new IllegalArgumentException("방문일과 공개 범위는 비울 수 없습니다.");
        }
        if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
            throw new IllegalArgumentException("좌표가 범위를 벗어났습니다.");
        }
        if (!isValidNights(nights)) {
            throw new IllegalArgumentException("박 수는 " + MIN_NIGHTS + "~" + MAX_NIGHTS + "이어야 합니다.");
        }
        if (content != null && !isValidContent(content)) {
            throw new IllegalArgumentException("글은 공백뿐이 아닌 " + CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }

    /** today(한국 날짜)보다 뒤에 방문했다고 적었으면 true다. 오늘 방문한 기록은 false다. */
    public boolean isVisitedAfter(LocalDate today) {
        return visitedDate.isAfter(today);
    }

    public static boolean isValidLatitude(double latitude) {
        return latitude >= -MAX_LATITUDE && latitude <= MAX_LATITUDE;
    }

    public static boolean isValidLongitude(double longitude) {
        return longitude >= -MAX_LONGITUDE && longitude <= MAX_LONGITUDE;
    }

    public static boolean isValidNights(int nights) {
        return nights >= MIN_NIGHTS && nights <= MAX_NIGHTS;
    }

    /** content가 공백뿐이 아니고 {@value #CONTENT_MAX_LENGTH}자 이하이면 true다. 글이 없는 경우(null)는 호출하는 쪽이 따로 본다. */
    public static boolean isValidContent(String content) {
        return !content.isBlank() && content.length() <= CONTENT_MAX_LENGTH;
    }
}

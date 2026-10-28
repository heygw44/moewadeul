package com.moewadeul.memory.domain;

import java.time.LocalDate;

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

    public static boolean isValidContent(String content) {
        return !content.isBlank() && content.length() <= CONTENT_MAX_LENGTH;
    }
}

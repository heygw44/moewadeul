package com.moewadeul.spot.application.publicspot;

import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.application.PublicSpotSetting;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.WeatherGrid;
import com.moewadeul.spot.infra.publicspot.PublicSpotColumns;
import com.moewadeul.spot.infra.publicspot.PublicSpotRow;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

record NormalizedPublicSpot(
        String externalId,
        String name,
        GeoPoint location,
        WeatherGrid weatherGrid,
        String address,
        String category,
        SortedMap<String, String> facilities,
        String phone,
        String homepage,
        PublicSpotOperatingStatus operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil,
        LocalDate sourceDate,
        Set<PublicSpotSetting> settings) {

    static final int MAX_EXTERNAL_ID_LENGTH = 50;
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_ADDRESS_LENGTH = 255;
    static final int MAX_CATEGORY_LENGTH = 50;
    static final int MAX_PHONE_LENGTH = 30;
    static final int MAX_HOMEPAGE_LENGTH = 255;

    // MySQL은 SRID 4326 점의 경도를 읽을 때 부동소수 오차를 낸다. 예를 들어 127.7298을 저장하면 ST_Longitude가 127.72979999999998을 돌려준다.
    // 그래서 좌표를 정확히 같은지로 비교하면 원천 값이 그대로여도 동기화할 때마다 바뀐 장소로 보고 갱신한다.
    // 서버는 차이가 이 값(약 1cm) 이하이면 같은 좌표로 본다.
    static final double COORDINATE_TOLERANCE_DEGREES = 1e-7;

    static NormalizedPublicSpot from(PublicSpotCommand command) {
        String externalId = requireText(command.externalId(), "외부 ID가 비어 있습니다.");
        if (lengthOf(externalId) > MAX_EXTERNAL_ID_LENGTH) {
            throw new IllegalArgumentException("외부 ID가 " + MAX_EXTERNAL_ID_LENGTH + "자를 넘습니다.");
        }
        String name = requireText(command.name(), "이름이 비어 있습니다.");
        GeoPoint location = new GeoPoint(command.latitude(), command.longitude());
        return new NormalizedPublicSpot(
                externalId,
                truncate(name, MAX_NAME_LENGTH),
                location,
                WeatherGrid.from(location),
                truncate(blankToNull(command.address()), MAX_ADDRESS_LENGTH),
                truncate(blankToNull(command.category()), MAX_CATEGORY_LENGTH),
                normalizeFacilities(command.facilities()),
                truncate(blankToNull(command.phone()), MAX_PHONE_LENGTH),
                dropIfTooLong(blankToNull(command.homepage()), MAX_HOMEPAGE_LENGTH),
                command.operatingStatus(),
                command.closedFrom(),
                command.closedUntil(),
                command.sourceDate(),
                command.settings());
    }

    boolean differsInSpotColumns(PublicSpotRow stored) {
        return !Objects.equals(name, stored.name())
                || !Objects.equals(address, stored.address())
                || !isSameDegree(location.latitude(), stored.latitude())
                || !isSameDegree(location.longitude(), stored.longitude());
    }

    boolean differsInDetailColumns(PublicSpotRow stored, Map<String, String> storedFacilities) {
        return !Objects.equals(category, stored.category())
                || !Objects.equals(facilities, storedFacilities)
                || !Objects.equals(phone, stored.phone())
                || !Objects.equals(homepage, stored.homepage())
                || !Objects.equals(operatingStatusName(), stored.operatingStatus())
                || !Objects.equals(closedFrom, stored.closedFrom())
                || !Objects.equals(closedUntil, stored.closedUntil())
                || !Objects.equals(sourceDate, stored.sourceDate());
    }

    boolean differsInSettings(Set<PublicSpotSetting> stored) {
        return !settings.equals(stored);
    }

    PublicSpotColumns toColumns(String facilitiesJson) {
        return new PublicSpotColumns(
                externalId,
                name,
                location.latitude(),
                location.longitude(),
                address,
                weatherGrid.nx(),
                weatherGrid.ny(),
                category,
                facilitiesJson,
                phone,
                homepage,
                operatingStatusName(),
                closedFrom,
                closedUntil,
                sourceDate);
    }

    private String operatingStatusName() {
        if (operatingStatus == null) {
            return null;
        }
        return operatingStatus.name();
    }

    private static String requireText(String value, String message) {
        String text = blankToNull(value);
        if (text == null) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static SortedMap<String, String> normalizeFacilities(Map<String, String> source) {
        if (source == null) {
            return null;
        }
        SortedMap<String, String> facilities = new TreeMap<>();
        source.forEach((key, value) -> {
            String item = blankToNull(value);
            if (key != null && !key.isBlank() && item != null) {
                facilities.put(key.strip(), item);
            }
        });
        if (facilities.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableSortedMap(facilities);
    }

    // 이모지처럼 Java char 두 개로 표현하는 문자도 MySQL은 한 글자로 센다. 그래서 길이를 코드 포인트 수로 센다.
    private static String truncate(String value, int maxLength) {
        if (value == null || lengthOf(value) <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength)).strip();
    }

    private static String dropIfTooLong(String value, int maxLength) {
        if (value == null || lengthOf(value) <= maxLength) {
            return value;
        }
        return null;
    }

    private static int lengthOf(String value) {
        return value.codePointCount(0, value.length());
    }

    private static boolean isSameDegree(double source, double stored) {
        return Math.abs(source - stored) <= COORDINATE_TOLERANCE_DEGREES;
    }
}

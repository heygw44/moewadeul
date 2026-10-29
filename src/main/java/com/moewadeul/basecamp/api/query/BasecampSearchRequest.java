package com.moewadeul.basecamp.api.query;

import com.moewadeul.basecamp.application.query.BasecampSearchQuery;
import com.moewadeul.basecamp.application.query.BasecampSpotType;
import com.moewadeul.common.paging.PageSlice;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.format.annotation.DateTimeFormat;

public record BasecampSearchRequest(
        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 해요.") @DecimalMax(value = "90", message = "위도는 90 이하여야 해요.")
        Double swLat,

        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 해요.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 해요.")
        Double swLng,

        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 해요.") @DecimalMax(value = "90", message = "위도는 90 이하여야 해요.")
        Double neLat,

        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 해요.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 해요.")
        Double neLng,

        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 해요.") @DecimalMax(value = "90", message = "위도는 90 이하여야 해요.")
        Double lat,

        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 해요.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 해요.")
        Double lng,

        @DecimalMin(value = "0", inclusive = false, message = "반경은 0보다 커야 해요.")
        @DecimalMax(value = "50", message = "반경은 50km 이하여야 해요.")
        Double radiusKm,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        Boolean hasVacancy,
        Set<@NotNull(message = "장소 유형에 빈 값이 있어요.") BasecampSpotType> types,

        Set<
                        @NotNull(message = "박 수에 빈 값이 있어요.") @Min(value = 1, message = "박 수는 1 이상이어야 해요.")
                        @Max(value = 3, message = "박 수는 3 이하여야 해요.") Integer>
                nights,

        @Min(value = 1, message = "최소 신뢰 단계 상한은 1 이상이어야 해요.") @Max(value = 2, message = "최소 신뢰 단계 상한은 2 이하여야 해요.")
        Integer maxTrustLevel,

        Boolean noAgeLimit,
        Boolean noSameGenderOnly,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 해요.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 해요.")
        @Max(value = PageSlice.MAX_SIZE, message = "페이지 크기는 " + PageSlice.MAX_SIZE + " 이하여야 해요.")
        Integer size) {

    @AssertTrue(message = "지역은 swLat, swLng, neLat, neLng 네 개나 lat, lng, radiusKm 세 개 중 한 묶음만 모두 보내 주세요.")
    public boolean isRegionSpecified() {
        return (hasFullArea() && hasNoRadius()) || (hasNoArea() && hasFullRadius());
    }

    @AssertTrue(message = "남서쪽 위도는 북동쪽 위도보다 작아야 해요.")
    public boolean isLatitudeOrdered() {
        return !hasFullArea() || swLat < neLat;
    }

    @AssertTrue(message = "남서쪽 경도는 북동쪽 경도보다 작아야 해요.")
    public boolean isLongitudeOrdered() {
        return !hasFullArea() || swLng < neLng;
    }

    @AssertTrue(message = "출발일 범위의 시작(fromDate)은 끝(toDate)보다 늦을 수 없어요.")
    public boolean isDateRangeOrdered() {
        return fromDate == null || toDate == null || !fromDate.isAfter(toDate);
    }

    BasecampSearchQuery toQuery() {
        BasecampSearchQuery.Region region = hasFullArea()
                ? new BasecampSearchQuery.Area(swLat, swLng, neLat, neLng)
                : new BasecampSearchQuery.Radius(lat, lng, radiusKm);
        return new BasecampSearchQuery(
                region,
                fromDate,
                toDate,
                Boolean.TRUE.equals(hasVacancy),
                types == null ? Set.of() : types,
                nights == null ? Set.of() : nights,
                maxTrustLevel,
                Boolean.TRUE.equals(noAgeLimit),
                Boolean.TRUE.equals(noSameGenderOnly),
                page == null ? PageSlice.DEFAULT_PAGE : page,
                size == null ? PageSlice.DEFAULT_SIZE : size);
    }

    private boolean hasFullArea() {
        return Stream.of(swLat, swLng, neLat, neLng).allMatch(value -> value != null);
    }

    private boolean hasNoArea() {
        return Stream.of(swLat, swLng, neLat, neLng).allMatch(value -> value == null);
    }

    private boolean hasFullRadius() {
        return Stream.of(lat, lng, radiusKm).allMatch(value -> value != null);
    }

    private boolean hasNoRadius() {
        return Stream.of(lat, lng, radiusKm).allMatch(value -> value == null);
    }
}

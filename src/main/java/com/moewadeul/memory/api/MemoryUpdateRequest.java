package com.moewadeul.memory.api;

import com.moewadeul.common.patch.PatchField;
import com.moewadeul.memory.application.MemoryReviseCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record MemoryUpdateRequest(
        @Schema(implementation = Long.class, description = "연결할 장소 ID. null로 보내면 장소 연결을 끊는다.")
        PatchField<Long> spotId,

        @Schema(implementation = Double.class, description = "위도 -90~90. lng와 함께 보낸다.")
        PatchField<Double> lat,

        @Schema(implementation = Double.class, description = "경도 -180~180. lat와 함께 보낸다.")
        PatchField<Double> lng,

        @Schema(implementation = LocalDate.class, description = "방문일(한국 날짜). 오늘보다 뒤일 수 없다.")
        PatchField<LocalDate> visitedDate,

        @Schema(implementation = Integer.class, description = "박 수 0~30")
        PatchField<Integer> nights,

        @Schema(implementation = String.class, description = "글. 공백뿐일 수 없고 2000자 이하다. null로 보내면 글을 지운다.")
        PatchField<String> content,

        @Schema(
                implementation = String.class,
                allowableValues = {"PRIVATE", "PUBLIC"},
                description = "공개 범위")
        PatchField<String> visibility,

        @Schema(
                implementation = Long[].class,
                description = "붙일 사진 ID. 보내면 기록의 사진을 배열 순서대로 통째로 바꾸고, 빈 배열은 사진을 모두 뗀다. null로 보내면 400이다.")
        PatchField<List<Long>> imageIds) {

    // Jackson은 JSON에 빠진 레코드 구성요소를 PatchField 역직렬화기를 거치지 않고 Java null로 넘기는 경우가 있다.
    public MemoryUpdateRequest {
        spotId = absentIfNull(spotId);
        lat = absentIfNull(lat);
        lng = absentIfNull(lng);
        visitedDate = absentIfNull(visitedDate);
        nights = absentIfNull(nights);
        content = absentIfNull(content);
        visibility = absentIfNull(visibility);
        imageIds = absentIfNull(imageIds);
    }

    MemoryReviseCommand toCommand() {
        return new MemoryReviseCommand(spotId, lat, lng, visitedDate, nights, content, visibility, imageIds);
    }

    private static <T> PatchField<T> absentIfNull(PatchField<T> field) {
        return field == null ? PatchField.absent() : field;
    }
}

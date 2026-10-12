package com.moewadeul.memory.api;

import com.moewadeul.memory.application.MemoryWriteCommand;
import com.moewadeul.memory.domain.MemoryDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

// 빠진 값을 @NotNull 위반으로 돌려주려고 숫자는 래퍼 타입으로 받는다. 방문일이 오늘 뒤인지, 글이 공백뿐인지, 장소가 연결할 수 있는 장소인지는
// 서비스가 검사한다. 공개 범위는 문자열로 받아서, 모르는 값을 Jackson의 enum 변환 오류가 아니라 이 필드의 입력 오류로 돌려준다.
public record MemoryCreateRequest(
        @NotNull(message = "위도를 입력해야 합니다.")
        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 합니다.")
        Double lat,

        @NotNull(message = "경도를 입력해야 합니다.")
        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 합니다.")
        Double lng,

        @Schema(description = "방문일(한국 날짜). 오늘보다 뒤일 수 없다.", example = "2026-10-05") @NotNull(message = "방문일을 입력해야 합니다.")
        LocalDate visitedDate,

        @NotNull(message = "박 수를 입력해야 합니다.")
        @Min(value = MemoryDetails.MIN_NIGHTS, message = "박 수는 0 이상이어야 합니다.")
        @Max(value = MemoryDetails.MAX_NIGHTS, message = "박 수는 30 이하여야 합니다.")
        Integer nights,

        @Schema(description = "글(선택). 보내면 공백뿐일 수 없다.")
        @Size(max = MemoryDetails.CONTENT_MAX_LENGTH, message = "글은 2000자 이하여야 합니다.")
        String content,

        @Schema(
                description = "공개 범위. PRIVATE(기본) 또는 PUBLIC",
                allowableValues = {"PRIVATE", "PUBLIC"})
        @Pattern(regexp = "PRIVATE|PUBLIC", message = "공개 범위는 PRIVATE나 PUBLIC이어야 합니다.")
        String visibility,

        @Schema(description = "연결할 장소 ID(선택). 지도에 보이는 장소여야 한다.")
        Long spotId,

        @Schema(description = "붙일 사진 ID. 최대 10개이고 배열 순서가 기록 안 순서다. 없으면 사진 없이 쓴다.")
        List<Long> imageIds) {

    MemoryWriteCommand toCommand() {
        return new MemoryWriteCommand(spotId, lat, lng, visitedDate, nights, content, visibility, imageIds);
    }
}

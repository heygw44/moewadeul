package com.moewadeul.memory.application;

import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.memory.domain.MemoryDetails;
import com.moewadeul.memory.domain.MemoryVisibility;
import java.time.LocalDate;
import java.util.List;

/**
 * 기록 작성 요청. 값은 받은 그대로 담고, {@link #toDetails}가 규칙을 검사한다. 수정 요청도 지금 기록과 합친 뒤 이 모양으로 바꿔 같은 검사를 받는다.
 *
 * @param spotId 연결할 장소. 연결하지 않으면 null이다
 * @param content 글. 쓰지 않으면 null이다
 * @param visibility 공개 범위 이름. null이면 PRIVATE다
 * @param imageIds 붙일 사진 ID. null이면 빈 목록으로 바꾼다
 */
public record MemoryWriteCommand(
        Long spotId,
        Double lat,
        Double lng,
        LocalDate visitedDate,
        Integer nights,
        String content,
        String visibility,
        List<Long> imageIds) {

    public MemoryWriteCommand {
        imageIds = imageIds == null ? List.of() : List.copyOf(imageIds);
    }

    /**
     * 호출하면 값을 검사해 기록 내용으로 바꾼다. today는 한국 날짜의 오늘이다.
     *
     * <p>좌표, 방문일, 박 수가 빠졌거나 범위를 벗어났거나, 방문일이 오늘보다 뒤이거나, 글이 공백뿐이거나 2,000자를 넘거나, 공개 범위가 PRIVATE나
     * PUBLIC이 아니면 그 필드의 INVALID_INPUT으로 거부한다. 장소가 연결할 수 있는 장소인지는 장소 상태를 아는 호출하는 쪽이 검사한다.
     */
    MemoryDetails toDetails(LocalDate today) {
        requireCoordinates();
        if (visitedDate == null) {
            throw invalid("visitedDate", "방문일을 입력해야 합니다.");
        }
        if (visitedDate.isAfter(today)) {
            throw invalid("visitedDate", "방문일은 오늘(한국 날짜)보다 뒤일 수 없습니다.");
        }
        if (nights == null || !MemoryDetails.isValidNights(nights)) {
            throw invalid("nights", "박 수는 " + MemoryDetails.MIN_NIGHTS + "~" + MemoryDetails.MAX_NIGHTS + "이어야 합니다.");
        }
        if (content != null && !MemoryDetails.isValidContent(content)) {
            throw invalid("content", "글은 공백뿐일 수 없고 " + MemoryDetails.CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return new MemoryDetails(spotId, lat, lng, visitedDate, nights, content, parseVisibility());
    }

    private void requireCoordinates() {
        if (lat == null || !MemoryDetails.isValidLatitude(lat)) {
            throw invalid("lat", "위도는 -90~90이어야 합니다.");
        }
        if (lng == null || !MemoryDetails.isValidLongitude(lng)) {
            throw invalid("lng", "경도는 -180~180이어야 합니다.");
        }
    }

    private MemoryVisibility parseVisibility() {
        if (visibility == null) {
            return MemoryVisibility.PRIVATE;
        }
        return MemoryVisibility.from(visibility)
                .orElseThrow(() -> invalid("visibility", "공개 범위는 PRIVATE나 PUBLIC이어야 합니다."));
    }

    static InvalidFieldException invalid(String field, String reason) {
        return new InvalidFieldException(field, reason);
    }
}

package com.moewadeul.memory.application;

import com.moewadeul.common.error.EnumParser;
import com.moewadeul.common.error.InvalidFieldException;
import com.moewadeul.memory.domain.MemoryDetails;
import com.moewadeul.memory.domain.MemoryVisibility;
import java.time.LocalDate;
import java.util.List;

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

    MemoryDetails toDetails() {
        requireCoordinates();
        if (visitedDate == null) {
            throw invalid("visitedDate", "방문일을 입력해 주세요.");
        }
        if (nights == null || !MemoryDetails.isValidNights(nights)) {
            throw invalid("nights", "박 수는 " + MemoryDetails.MIN_NIGHTS + "~" + MemoryDetails.MAX_NIGHTS + "이어야 해요.");
        }
        if (content != null && !MemoryDetails.isValidContent(content)) {
            throw invalid("content", "글은 공백뿐일 수 없고 " + MemoryDetails.CONTENT_MAX_LENGTH + "자 이하여야 해요.");
        }
        return new MemoryDetails(spotId, lat, lng, visitedDate, nights, content, parseVisibility());
    }

    private void requireCoordinates() {
        if (lat == null || !MemoryDetails.isValidLatitude(lat)) {
            throw invalid("lat", "위도는 -90~90이어야 해요.");
        }
        if (lng == null || !MemoryDetails.isValidLongitude(lng)) {
            throw invalid("lng", "경도는 -180~180이어야 해요.");
        }
    }

    private MemoryVisibility parseVisibility() {
        if (visibility == null) {
            return MemoryVisibility.PRIVATE;
        }
        return EnumParser.find(MemoryVisibility.class, visibility)
                .orElseThrow(() -> invalid("visibility", "공개 범위는 PRIVATE나 PUBLIC이어야 해요."));
    }

    static InvalidFieldException invalid(String field, String reason) {
        return new InvalidFieldException(field, reason);
    }
}

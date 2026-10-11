package com.moewadeul.trip.api;

import com.moewadeul.trip.application.SoloTripRegisterCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

// 날짜가 빠지거나 범위를 벗어나면 INVALID_INPUT이 아니라 일정 오류로 알려야 해서, 날짜는 여기서 검증하지 않고 도메인이 검사한다.
public record SoloTripRegisterRequest(
        @Schema(description = "장소 ID", example = "101") @NotNull(message = "장소를 골라야 합니다.")
        Long spotId,

        @Schema(description = "출발일(한국 날짜). 내일부터 60일 안", example = "2026-11-07")
        LocalDate startDate,

        @Schema(description = "종료일(한국 날짜). 출발일의 1~3일 뒤", example = "2026-11-08")
        LocalDate endDate) {

    SoloTripRegisterCommand toCommand() {
        return new SoloTripRegisterCommand(spotId, startDate, endDate);
    }
}

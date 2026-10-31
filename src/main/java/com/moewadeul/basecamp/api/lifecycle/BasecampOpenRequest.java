package com.moewadeul.basecamp.api.lifecycle;

import com.moewadeul.basecamp.application.lifecycle.BasecampOpenCommand;
import com.moewadeul.basecamp.domain.Basecamp;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record BasecampOpenRequest(
        @NotNull(message = "장소를 골라 주세요.") Long spotId,

        @NotBlank(message = "제목을 입력해 주세요.") @Size(max = Basecamp.TITLE_MAX_LENGTH, message = "제목은 100자 이하여야 해요.")
        String title,

        @NotBlank(message = "설명을 입력해 주세요.") @Size(max = Basecamp.DESCRIPTION_MAX_LENGTH, message = "설명은 2000자 이하여야 해요.")
        String description,

        @Schema(description = "출발일(한국 날짜). 내일부터 60일 뒤까지다.", example = "2026-10-20") @NotNull(message = "출발일을 골라 주세요.")
        LocalDate startDate,

        @Schema(description = "종료일(한국 날짜). 출발일로부터 1~3박이다.", example = "2026-10-22")
        @NotNull(message = "돌아오는 날을 골라 주세요.")
        LocalDate endDate,

        @Schema(description = "캠프 리더를 포함한 정원. 2~6명이다.", example = "4") @NotNull(message = "정원을 골라 주세요.")
        Integer capacity,

        @Valid JoinConditionRequest joinCondition) {

    public record JoinConditionRequest(
            @Schema(description = "최소 신뢰 단계. 1 또는 2다.", example = "1")
            Integer minTrustLevel,

            @Schema(description = "연령대 하한. 20~60의 십 단위이고, 상한과 함께 보내거나 함께 생략한다.", example = "20")
            Integer ageGroupMin,

            @Schema(description = "연령대 상한. 20~60의 십 단위이고 60은 60대 이상이다.", example = "30")
            Integer ageGroupMax,

            @Schema(description = "true이면 캠프 리더와 같은 성별(본인확인 값)만 받는다. 보내지 않으면 false다.", example = "false")
            Boolean sameGenderOnly) {

        BasecampOpenCommand.JoinConditionCommand toCommand() {
            return new BasecampOpenCommand.JoinConditionCommand(
                    minTrustLevel, ageGroupMin, ageGroupMax, Boolean.TRUE.equals(sameGenderOnly));
        }
    }

    BasecampOpenCommand toCommand() {
        BasecampOpenCommand.JoinConditionCommand condition = joinCondition == null ? null : joinCondition.toCommand();
        return new BasecampOpenCommand(spotId, title, description, startDate, endDate, capacity, condition);
    }
}

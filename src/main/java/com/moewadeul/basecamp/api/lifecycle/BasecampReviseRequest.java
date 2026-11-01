package com.moewadeul.basecamp.api.lifecycle;

import com.moewadeul.basecamp.application.lifecycle.BasecampOpenCommand;
import com.moewadeul.basecamp.application.lifecycle.BasecampReviseCommand;
import com.moewadeul.basecamp.domain.Basecamp;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BasecampReviseRequest(
        @Size(max = Basecamp.TITLE_MAX_LENGTH, message = "제목은 100자 이하여야 해요.")
        @Pattern(regexp = ".*\\S.*", message = "제목은 공백뿐일 수 없어요.")
        String title,

        @Size(max = Basecamp.DESCRIPTION_MAX_LENGTH, message = "설명은 2000자 이하여야 해요.")
        @Pattern(regexp = "(?s).*\\S.*", message = "설명은 공백뿐일 수 없어요.")
        String description,

        @Schema(description = "보내면 합류 조건 전체를 바꾼다. 열 때와 같은 규칙이다.") @Valid
        BasecampOpenRequest.JoinConditionRequest joinCondition,

        @Schema(description = "캠프 리더를 포함한 정원. 2~6명이고 지금보다 늘리기만 할 수 있다.", example = "5")
        Integer capacity) {

    @Schema(hidden = true)
    @AssertTrue(message = "수정할 항목을 하나 이상 입력해 주세요.")
    public boolean isAnyFieldPresent() {
        return title != null || description != null || joinCondition != null || capacity != null;
    }

    BasecampReviseCommand toCommand() {
        BasecampOpenCommand.JoinConditionCommand condition = joinCondition == null ? null : joinCondition.toCommand();
        return new BasecampReviseCommand(title, description, condition, capacity);
    }
}

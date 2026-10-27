package com.moewadeul.admin.api.program;

import com.moewadeul.common.web.PatchField;
import com.moewadeul.program.application.catalog.ProgramLimits;
import com.moewadeul.program.application.catalog.ProgramReviseCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record AdminProgramReviseRequest(
        @Size(max = ProgramLimits.TITLE_MAX_LENGTH, message = "제목은 100자 이하여야 해요.")
        @Pattern(regexp = ".*\\S.*", message = "제목은 공백뿐일 수 없어요.")
        String title,

        @Size(max = ProgramLimits.DESCRIPTION_MAX_LENGTH, message = "설명은 5000자 이하여야 해요.")
        @Pattern(regexp = "(?s).*\\S.*", message = "설명은 공백뿐일 수 없어요.")
        String description,

        @Schema(implementation = Long.class, description = "null로 보내면 지도 장소와의 연결을 끊는다. 값이 있으면 지도에 보이는 장소여야 한다.")
        PatchField<Long> spotId,

        @Size(max = ProgramLimits.LOCATION_TEXT_MAX_LENGTH, message = "장소 설명은 255자 이하여야 해요.")
        @Pattern(regexp = ".*\\S.*", message = "장소 설명은 공백뿐일 수 없어요.")
        String locationText,

        Instant startAt,
        Instant endAt,

        @Schema(description = "정원. 1~1000이고, 신청이 시작된 뒤에는 줄일 수 없다.", example = "250")
        @Min(value = ProgramLimits.CAPACITY_MIN, message = "정원은 1 이상이어야 해요.")
        @Max(value = ProgramLimits.CAPACITY_MAX, message = "정원은 1000 이하여야 해요.")
        Integer capacity,

        @Min(value = ProgramLimits.FEE_MIN, message = "참가비는 0 이상이어야 해요.")
        @Max(value = ProgramLimits.FEE_MAX, message = "참가비는 1000000 이하여야 해요.")
        Integer fee,

        Instant applyOpenAt,
        Instant applyCloseAt,

        @Min(value = ProgramLimits.PAYMENT_DEADLINE_MINUTES_MIN, message = "결제 기한은 1분 이상이어야 해요.")
        @Max(value = ProgramLimits.PAYMENT_DEADLINE_MINUTES_MAX, message = "결제 기한은 1440분 이하여야 해요.")
        Integer paymentDeadlineMinutes,

        Boolean overnight) {

    public AdminProgramReviseRequest {
        spotId = spotId == null ? PatchField.absent() : spotId;
    }

    @Schema(hidden = true)
    @AssertTrue(message = "고칠 필드를 하나 이상 보내 주세요.")
    public boolean isAnyFieldPresent() {
        return title != null
                || description != null
                || spotId.present()
                || locationText != null
                || startAt != null
                || endAt != null
                || capacity != null
                || fee != null
                || applyOpenAt != null
                || applyCloseAt != null
                || paymentDeadlineMinutes != null
                || overnight != null;
    }

    ProgramReviseCommand toCommand() {
        return new ProgramReviseCommand(
                title,
                description,
                spotId,
                locationText,
                startAt,
                endAt,
                capacity,
                fee,
                applyOpenAt,
                applyCloseAt,
                paymentDeadlineMinutes,
                overnight);
    }
}

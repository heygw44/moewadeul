package com.moewadeul.trust.api.profile;

import com.moewadeul.common.web.PatchField;
import com.moewadeul.member.application.profile.MyInfoUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;

public record MeUpdateRequest(
        @Schema(
                implementation = String.class,
                minLength = MyInfoUpdateCommand.NICKNAME_MIN_LENGTH,
                maxLength = MyInfoUpdateCommand.NICKNAME_MAX_LENGTH,
                description = "앞뒤 공백, 연속 공백, 보이지 않는 문자는 쓸 수 없다. null로 보내면 400이다.")
        PatchField<String> nickname,

        @Schema(
                implementation = String.class,
                description = "자기 신고 연령대. null로 보내면 지운다.",
                allowableValues = {"TWENTIES", "THIRTIES", "FORTIES", "FIFTIES", "SIXTIES_PLUS"})
        PatchField<String> selfAgeGroup,

        @Schema(
                implementation = String.class,
                description = "자기 신고 성별. null로 보내면 지운다.",
                allowableValues = {"FEMALE", "MALE"})
        PatchField<String> selfGender) {

    public MeUpdateRequest {
        nickname = absentIfNull(nickname);
        selfAgeGroup = absentIfNull(selfAgeGroup);
        selfGender = absentIfNull(selfGender);
    }

    public MyInfoUpdateCommand toCommand() {
        return new MyInfoUpdateCommand(nickname, selfAgeGroup, selfGender);
    }

    private static <T> PatchField<T> absentIfNull(PatchField<T> field) {
        return field == null ? PatchField.absent() : field;
    }
}

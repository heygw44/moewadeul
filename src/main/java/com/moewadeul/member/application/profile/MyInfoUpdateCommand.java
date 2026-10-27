package com.moewadeul.member.application.profile;

import com.moewadeul.common.web.PatchField;
import com.moewadeul.member.domain.Member;
import java.util.Objects;

public record MyInfoUpdateCommand(
        PatchField<String> nickname, PatchField<String> selfAgeGroup, PatchField<String> selfGender) {

    public static final int NICKNAME_MIN_LENGTH = Member.NICKNAME_MIN_LENGTH;

    public static final int NICKNAME_MAX_LENGTH = Member.NICKNAME_MAX_LENGTH;

    public MyInfoUpdateCommand {
        Objects.requireNonNull(nickname, "nickname");
        Objects.requireNonNull(selfAgeGroup, "selfAgeGroup");
        Objects.requireNonNull(selfGender, "selfGender");
    }
}

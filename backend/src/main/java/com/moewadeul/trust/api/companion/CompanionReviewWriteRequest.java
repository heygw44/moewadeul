package com.moewadeul.trust.api.companion;

import com.moewadeul.trust.application.companion.CompanionReviewWriteCommand;
import com.moewadeul.trust.domain.companion.CompanionReview;
import com.moewadeul.trust.domain.companion.CompanionReviewTag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;

public record CompanionReviewWriteRequest(
        @NotNull(message = "후기를 받을 회원을 골라 주세요.") @Positive(message = "후기를 받을 회원 ID는 양수여야 해요.")
        Long revieweeId,

        @NotNull(message = "다시 동행하고 싶은지 골라 주세요.") Boolean rejoinWanted,

        @Size(max = CompanionReviewWriteRequest.MAX_TAGS, message = "태그는 7개 이하여야 해요.")
        @UniqueElements(message = "같은 태그를 두 번 넣을 수 없어요.")
        List<@NotNull(message = "태그 값이 비어 있어요.") CompanionReviewTag> tags,

        @Size(max = CompanionReview.COMMENT_MAX_LENGTH, message = "코멘트는 300자 이하여야 해요.")
        String comment) {

    static final int MAX_TAGS = 7;

    CompanionReviewWriteCommand toCommand() {
        return new CompanionReviewWriteCommand(revieweeId, rejoinWanted, tags == null ? List.of() : tags, comment);
    }
}

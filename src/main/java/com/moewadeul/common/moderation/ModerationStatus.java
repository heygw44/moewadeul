package com.moewadeul.common.moderation;

import com.moewadeul.common.error.EnumParser;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public interface ModerationStatus {

    boolean isActive();

    boolean isPendingReview();

    boolean isHidden();

    String name();

    static <E extends Enum<E> & ModerationStatus> E parseReviewable(Class<E> type, String status) {
        if (status == null) {
            return Arrays.stream(type.getEnumConstants())
                    .filter(ModerationStatus::isPendingReview)
                    .findFirst()
                    .orElseThrow();
        }
        Set<E> reviewable = Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.isPendingReview() || constant.isHidden())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(type)));
        String names = reviewable.stream().map(Enum::name).collect(Collectors.joining(" 또는 "));
        return EnumParser.parseAmong(reviewable, status, "status는 " + names + "이어야 해요.");
    }
}

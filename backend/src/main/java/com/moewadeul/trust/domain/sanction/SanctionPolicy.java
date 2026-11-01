package com.moewadeul.trust.domain.sanction;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import java.util.List;

public final class SanctionPolicy {

    private static final List<SanctionType> TYPES_BY_LEVEL =
            List.of(SanctionType.WARNING, SanctionType.SUSPEND_7D, SanctionType.SUSPEND_30D, SanctionType.PERMANENT);

    public static final int MAX_LEVEL = TYPES_BY_LEVEL.size();

    private SanctionPolicy() {}

    public static int nextLevel(Integer highestConfirmedLevel) {
        if (highestConfirmedLevel == null) {
            return 1;
        }
        return Math.min(highestConfirmedLevel + 1, MAX_LEVEL);
    }

    public static SanctionType typeOf(int level) {
        if (level < 1 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("제재 단계는 1~" + MAX_LEVEL + "이어야 합니다. level=" + level);
        }
        return TYPES_BY_LEVEL.get(level - 1);
    }

    public static int levelOf(SanctionType type) {
        int index = TYPES_BY_LEVEL.indexOf(type);
        if (index < 0) {
            throw new IllegalArgumentException("단계가 없는 제재 종류입니다. type=" + type);
        }
        return index + 1;
    }

    public static int decideLevel(Integer highestConfirmedLevel, SanctionType requested) {
        if (requested == null || requested == SanctionType.TEMPORARY_72H) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "관리자가 내릴 수 없는 제재 종류예요.");
        }
        int next = nextLevel(highestConfirmedLevel);
        if (requested != SanctionType.PERMANENT && requested != typeOf(next)) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT, "요청한 제재 종류가 이 회원의 다음 단계(" + typeOf(next) + ")와 달라요.");
        }
        return levelOf(requested);
    }
}

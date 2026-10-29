package com.moewadeul.common.moderation;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ReportReasonCount(long targetId, String reason, long reportCount) {

    // 신고가 없는 사유도 0으로 채워 관리자 목록이 사유 키를 모두 받게 한다. 키 순서는 enum 선언 순서다.
    public static Map<Long, Map<String, Long>> byTarget(
            Class<? extends Enum<?>> reasonType, Collection<Long> targetIds, List<ReportReasonCount> counts) {
        Map<Long, Map<String, Long>> byTarget = new HashMap<>();
        for (long targetId : targetIds) {
            Map<String, Long> zeros = new LinkedHashMap<>();
            for (Enum<?> reason : reasonType.getEnumConstants()) {
                zeros.put(reason.name(), 0L);
            }
            byTarget.put(targetId, zeros);
        }
        for (ReportReasonCount count : counts) {
            byTarget.get(count.targetId()).put(count.reason(), count.reportCount());
        }
        return byTarget;
    }
}

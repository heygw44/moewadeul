package com.moewadeul.common.moderation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record RecentReportRow(long targetId, String reason, String content, Instant createdAt) {

    public static Map<Long, List<RecentReport>> byTarget(List<RecentReportRow> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        RecentReportRow::targetId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                row -> new RecentReport(row.reason(), row.content(), row.createdAt()),
                                Collectors.toList())));
    }
}

package com.moewadeul.spot.application.park;

import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.infra.park.BakjiJudgeTarget;
import com.moewadeul.spot.infra.park.BakjiPointRow;
import com.moewadeul.spot.infra.park.ParkAreaJudgeMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParkAreaJudgeService {

    private static final int UPDATE_CHUNK_SIZE = 1_000;

    private final ParkAreaJudgeMapper parkAreaJudgeMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ParkAreaJudgement judge(GeoPoint location) {
        Instant now = clock.instant();
        Long protectedAreaId = parkAreaJudgeMapper.selectContainingAreaId(location.latitude(), location.longitude());
        if (protectedAreaId == null) {
            return ParkAreaJudgement.outside(now);
        }
        return ParkAreaJudgement.inside(protectedAreaId, now);
    }

    @Transactional
    public BakjiRejudgeChunk rejudgeBakjiChunk(long afterId, int limit, Instant now) {
        List<BakjiPointRow> points = parkAreaJudgeMapper.selectBakjiPoints(afterId, limit);
        if (points.isEmpty()) {
            return new BakjiRejudgeChunk(new BakjiRejudgeCounts(0, 0, 0), null);
        }
        Map<Long, Boolean> warnedBefore = new HashMap<>();
        for (BakjiPointRow point : points) {
            warnedBefore.put(point.id(), point.parkWarning());
        }

        Map<Long, Long> areaIdBySpotId = judgeAll(points);
        List<Long> staleIds = record(points, areaIdBySpotId, now);
        if (!staleIds.isEmpty()) {
            List<BakjiPointRow> fresh = parkAreaJudgeMapper.selectBakjiPointsForUpdate(staleIds);
            Map<Long, Long> freshAreaIds = judgeAll(fresh);
            record(fresh, freshAreaIds, now);
            areaIdBySpotId.putAll(freshAreaIds);
        }
        long lastId = points.getLast().id();
        return new BakjiRejudgeChunk(countChanges(warnedBefore, areaIdBySpotId), lastId);
    }

    private Map<Long, Long> judgeAll(List<BakjiPointRow> points) {
        Map<Long, Long> areaIdBySpotId = new HashMap<>();
        for (BakjiPointRow point : points) {
            areaIdBySpotId.put(point.id(), parkAreaJudgeMapper.selectContainingAreaId(point.lat(), point.lng()));
        }
        return areaIdBySpotId;
    }

    private List<Long> record(List<BakjiPointRow> points, Map<Long, Long> areaIdBySpotId, Instant now) {
        Map<Long, List<BakjiJudgeTarget>> targetsByAreaId = new HashMap<>();
        for (BakjiPointRow point : points) {
            targetsByAreaId
                    .computeIfAbsent(areaIdBySpotId.get(point.id()), key -> new ArrayList<>())
                    .add(new BakjiJudgeTarget(point.id(), point.version()));
        }
        List<Long> staleIds = new ArrayList<>();
        for (Map.Entry<Long, List<BakjiJudgeTarget>> entry : targetsByAreaId.entrySet()) {
            List<BakjiJudgeTarget> targets = entry.getValue();
            for (int from = 0; from < targets.size(); from += UPDATE_CHUNK_SIZE) {
                List<BakjiJudgeTarget> chunk =
                        targets.subList(from, Math.min(from + UPDATE_CHUNK_SIZE, targets.size()));
                int updated = parkAreaJudgeMapper.updateJudgement(chunk, entry.getKey(), now);
                if (updated < chunk.size()) {
                    chunk.forEach(target -> staleIds.add(target.id()));
                }
            }
        }
        staleIds.sort(null);
        return staleIds;
    }

    private static BakjiRejudgeCounts countChanges(Map<Long, Boolean> warnedBefore, Map<Long, Long> areaIdBySpotId) {
        int newlyWarned = 0;
        int cleared = 0;
        for (Map.Entry<Long, Boolean> entry : warnedBefore.entrySet()) {
            boolean warnedNow = areaIdBySpotId.get(entry.getKey()) != null;
            if (!entry.getValue() && warnedNow) {
                newlyWarned++;
            } else if (entry.getValue() && !warnedNow) {
                cleared++;
            }
        }
        return new BakjiRejudgeCounts(warnedBefore.size(), newlyWarned, cleared);
    }
}

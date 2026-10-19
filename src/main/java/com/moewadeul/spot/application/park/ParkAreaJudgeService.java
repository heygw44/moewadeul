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

/**
 * 박지 좌표가 공원 경계 안에 있는지 판정한다.
 *
 * <p>서버는 박지를 저장할 때 한 번 판정하고, 경고 여부, 근거 경계, 판정 시각을 박지 행에 저장한다. 지도와 상세를 조회할 때는 저장한 결과만 읽고 다시
 * 계산하지 않는다. 경계를 새 데이터로 다시 적재하면 재판정 작업이 {@link #rejudgeBakjiChunk}를 묶음마다 호출해서 모든 박지를 새 경계로 다시
 * 판정한다.
 *
 * <p>경계가 겹치는 곳의 좌표는 여러 경계에 들어간다. 이때 서버는 ID가 가장 작은 경계를 근거로 고른다.
 */
@Service
@RequiredArgsConstructor
public class ParkAreaJudgeService {

    private static final int UPDATE_CHUNK_SIZE = 1_000;

    private final ParkAreaJudgeMapper parkAreaJudgeMapper;
    private final Clock clock;

    /** 호출하면 location을 포함하는 공원 경계를 찾아 판정 결과를 돌려준다. 판정 시각은 호출한 시각이다. */
    @Transactional(readOnly = true)
    public ParkAreaJudgement judge(GeoPoint location) {
        Instant now = clock.instant();
        Long protectedAreaId = parkAreaJudgeMapper.selectContainingAreaId(location.latitude(), location.longitude());
        if (protectedAreaId == null) {
            return ParkAreaJudgement.outside(now);
        }
        return ParkAreaJudgement.inside(protectedAreaId, now);
    }

    /**
     * 호출하면 상태와 상관없이 ID가 afterId보다 큰 박지를 ID 순서로 최대 limit건 지금 경계로 다시 판정하고, 이 묶음의 집계와 마지막 박지 ID를 돌려준다.
     * 남은 박지가 없으면 판정 수 0과 빈 마지막 ID를 돌려준다.
     *
     * <p>박지 전체를 한 트랜잭션에서 판정하면 박지 90만 건에서 두 문제가 났다. 좌표와 판정 결과를 모두 메모리에 들고 있어 운영 힙이 모자랐고, 마지막
     * 몇 분 동안 박지 행 수십만 개를 커밋까지 잠가 박지 신고가 잠금 대기 시간을 넘겼다. 그래서 호출하는 쪽이 이 메서드를 묶음마다 부르고, 서비스는 묶음
     * 하나를 한 트랜잭션에서 처리하고 커밋한다. 묶음 사이에는 잠금을 들고 있지 않는다. 한 실행의 모든 박지가 같은 판정 시각을 갖도록 판정 시각 now는
     * 호출하는 쪽이 넘긴다.
     *
     * <p>서비스는 묶음의 박지 좌표를 한 번 읽고, 박지마다 공간 인덱스를 쓰는 점 조회로 경계를 찾는다. 겹친 경계에서는 ID가 가장 작은 경계를 근거로 고른다.
     * 그다음 같은 경계로 판정된 박지끼리 묶어서 기록한다.
     *
     * <p>좌표를 읽은 뒤 기록하기 전까지 제보자가 박지를 옮기거나 다른 쓰기가 박지 행을 바꿀 수 있다. 그래서 서비스는 읽은 때의 낙관적 잠금 번호가 그대로인
     * 행에만 기록한다. 기록한 수가 묶음 수보다 적으면 그 묶음의 박지를 잠금 읽기로 지금 값을 다시 읽어 다시 판정한다. 잠근 뒤에는 다른 쓰기가 끼어들 수 없어서
     * 두 번째 기록은 실패하지 않는다. 처음부터 박지를 잠그지 않는 이유는, 박지마다 경계를 찾는 동안 그 박지의 수정과 신고가 기다리지 않게 하기 위해서다.
     *
     * <p>예전에 경고를 받았더라도 지금 경계에 들어가지 않으면 서비스는 경고를 끄고 근거 경계를 비운다. 판정은 제보 내용을 고치는 일이 아니라서 updated_at은
     * 바꾸지 않는다.
     *
     * <p>서비스는 처음 읽은 경고 여부와 마지막 판정을 비교해서, 경고가 새로 생긴 박지와 경고가 풀린 박지를 센다. 경고를 유지하면서 근거 경계만 바뀐 박지는
     * 어느 쪽에도 세지 않는다.
     */
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

    // 박지마다 경계를 찾는다. 경계 밖이면 값이 null이다.
    private Map<Long, Long> judgeAll(List<BakjiPointRow> points) {
        Map<Long, Long> areaIdBySpotId = new HashMap<>();
        for (BakjiPointRow point : points) {
            areaIdBySpotId.put(point.id(), parkAreaJudgeMapper.selectContainingAreaId(point.lat(), point.lng()));
        }
        return areaIdBySpotId;
    }

    // 같은 경계로 판정된 박지끼리 묶어 기록하고, 읽은 뒤 다른 쓰기가 바꿔서 기록하지 못한 행이 있는 묶음의 박지 ID를 돌려준다.
    // 어느 행이 바뀌었는지는 기록한 수만으로 알 수 없어서 묶음 전체를 돌려준다. 이미 기록한 행을 다시 판정해도 같은 좌표라서 결과가 같다.
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

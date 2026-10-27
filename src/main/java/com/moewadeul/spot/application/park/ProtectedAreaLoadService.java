package com.moewadeul.spot.application.park;

import com.moewadeul.spot.infra.park.ProtectedAreaInsertRow;
import com.moewadeul.spot.infra.park.ProtectedAreaKeyRow;
import com.moewadeul.spot.infra.park.ProtectedAreaMapper;
import com.moewadeul.spot.infra.park.ProtectedAreaUpdateRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProtectedAreaLoadService {

    private final ProtectedAreaMapper protectedAreaMapper;
    private final Clock clock;

    @Transactional
    public ProtectedAreaUpsertResult upsert(
            ProtectedAreaSource source, LocalDate sourceDate, List<ProtectedAreaCommand> commands) {
        requireUniqueKeys(commands);
        Map<AreaKey, List<Long>> storedIds = findStoredIds(source);
        Instant now = Instant.now(clock);
        int inserted = 0;
        for (ProtectedAreaCommand command : commands) {
            List<Long> ids = storedIds.get(AreaKey.of(command));
            if (ids == null) {
                protectedAreaMapper.insert(toInsertRow(source, sourceDate, command), now);
                inserted++;
                continue;
            }
            updateAll(ids, sourceDate, command, now);
        }
        return new ProtectedAreaUpsertResult(inserted, commands.size() - inserted);
    }

    private static void requireUniqueKeys(List<ProtectedAreaCommand> commands) {
        Set<AreaKey> keys = new HashSet<>();
        for (ProtectedAreaCommand command : commands) {
            if (!keys.add(AreaKey.of(command))) {
                throw new IllegalArgumentException(
                        "같은 구분과 이름의 경계가 두 번 있습니다. areaType=" + command.areaType() + ", name=" + command.name());
            }
        }
    }

    private Map<AreaKey, List<Long>> findStoredIds(ProtectedAreaSource source) {
        Map<AreaKey, List<Long>> storedIds = new HashMap<>();
        for (ProtectedAreaKeyRow row : protectedAreaMapper.selectKeysBySource(source.name())) {
            storedIds
                    .computeIfAbsent(new AreaKey(row.areaType(), row.name()), key -> new ArrayList<>())
                    .add(row.id());
        }
        return storedIds;
    }

    private void updateAll(List<Long> ids, LocalDate sourceDate, ProtectedAreaCommand command, Instant now) {
        for (long id : ids) {
            protectedAreaMapper.updateBoundary(
                    new ProtectedAreaUpdateRow(id, sourceDate, command.multiPolygonWkt()), now);
        }
    }

    private static ProtectedAreaInsertRow toInsertRow(
            ProtectedAreaSource source, LocalDate sourceDate, ProtectedAreaCommand command) {
        return new ProtectedAreaInsertRow(
                source.name(), command.areaType().name(), command.name(), sourceDate, command.multiPolygonWkt());
    }

    private record AreaKey(String areaType, String name) {

        static AreaKey of(ProtectedAreaCommand command) {
            return new AreaKey(command.areaType().name(), command.name());
        }
    }
}

package com.moewadeul.spot.application.park;

import com.moewadeul.spot.infra.park.ProtectedAreaInsertRow;
import com.moewadeul.spot.infra.park.ProtectedAreaKeyRow;
import com.moewadeul.spot.infra.park.ProtectedAreaMapper;
import com.moewadeul.spot.infra.park.ProtectedAreaUpdateRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
        Map<AreaKey, Long> storedIds = findStoredIds(source);
        Instant now = Instant.now(clock);
        int inserted = 0;
        for (ProtectedAreaCommand command : commands) {
            Long id = storedIds.get(AreaKey.of(command));
            if (id == null) {
                protectedAreaMapper.insert(toInsertRow(source, sourceDate, command), now);
                inserted++;
                continue;
            }
            protectedAreaMapper.updateBoundary(
                    new ProtectedAreaUpdateRow(id, sourceDate, command.multiPolygonWkt()), now);
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

    private Map<AreaKey, Long> findStoredIds(ProtectedAreaSource source) {
        return protectedAreaMapper.selectKeysBySource(source).stream()
                .collect(Collectors.toMap(row -> new AreaKey(row.areaType(), row.name()), ProtectedAreaKeyRow::id));
    }

    private static ProtectedAreaInsertRow toInsertRow(
            ProtectedAreaSource source, LocalDate sourceDate, ProtectedAreaCommand command) {
        return new ProtectedAreaInsertRow(
                source, command.areaType(), command.name(), sourceDate, command.multiPolygonWkt());
    }

    private record AreaKey(ProtectedAreaType areaType, String name) {

        static AreaKey of(ProtectedAreaCommand command) {
            return new AreaKey(command.areaType(), command.name());
        }
    }
}

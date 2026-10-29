package com.moewadeul.spot.application.publicspot;

import com.moewadeul.spot.application.PublicSpotSetting;
import com.moewadeul.spot.infra.publicspot.PublicSpotColumns;
import com.moewadeul.spot.infra.publicspot.PublicSpotInsertRow;
import com.moewadeul.spot.infra.publicspot.PublicSpotMapper;
import com.moewadeul.spot.infra.publicspot.PublicSpotRow;
import com.moewadeul.spot.infra.publicspot.PublicSpotSettingRow;
import com.moewadeul.spot.infra.publicspot.PublicSpotUpdateRow;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicSpotSyncService {

    private static final TypeReference<Map<String, String>> FACILITIES_TYPE = new TypeReference<>() {};

    private final PublicSpotMapper publicSpotMapper;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional
    public PublicSpotUpsertResult upsert(PublicSpotSource source, List<PublicSpotCommand> commands) {
        Map<String, NormalizedPublicSpot> spots = normalize(source, commands);
        int skipped = commands.size() - spots.size();
        if (spots.isEmpty()) {
            return new PublicSpotUpsertResult(0, 0, 0, skipped);
        }
        Map<String, PublicSpotRow> storedById = findStored(source, spots);
        Map<Long, Set<PublicSpotSetting>> storedSettings = findStoredSettings(storedById.values());
        UpsertPlan plan = new UpsertPlan(storedSettings);
        spots.values().forEach(spot -> plan.add(spot, storedById.get(spot.externalId())));
        Instant now = Instant.now(clock);
        insert(source, plan.inserts, now);
        insertSettingsOfNewSpots(plan.insertedSettings);
        update(plan, now);
        return new PublicSpotUpsertResult(plan.inserts.size(), plan.detailUpdates.size(), plan.unchanged, skipped);
    }

    @Transactional
    public int markRemoved(PublicSpotSource source, Collection<String> externalIds) {
        List<String> ids = externalIds.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(id -> !id.isEmpty() && lengthOf(id) <= NormalizedPublicSpot.MAX_EXTERNAL_ID_LENGTH)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return 0;
        }
        // 잠그지 않는 읽기로 후보를 먼저 찾는다. 없는 외부 ID를 곧바로 잠금 읽기에 넘기면 InnoDB가 없는 키 주변의 틈까지 잠가서
        // 같은 범위에 새 장소를 넣는 트랜잭션을 막는다. 후보가 있을 때만 기본 키로 잠가 읽는다.
        List<Long> candidateIds = publicSpotMapper.selectRemovalCandidateIds(source, ids);
        if (candidateIds.isEmpty()) {
            return 0;
        }
        List<Long> activeIds = publicSpotMapper.lockActiveSpots(candidateIds);
        if (activeIds.isEmpty()) {
            return 0;
        }
        List<Long> lockedIds = publicSpotMapper.lockUnremovedDetails(activeIds);
        if (lockedIds.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now(clock);
        int hidden = publicSpotMapper.hideSpots(lockedIds, now);
        publicSpotMapper.markSourceRemoved(lockedIds, now);
        if (hidden > 0) {
            log.info("public spot hidden by source removal source={} count={}", source, hidden);
        }
        return hidden;
    }

    @Transactional
    public int markMissingRemoved(PublicSpotSource source, Collection<String> presentExternalIds) {
        Set<String> present = presentExternalIds.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());
        if (present.isEmpty()) {
            throw new IllegalArgumentException("원천 목록이 비어 있어 빠진 장소를 숨기지 않습니다. source=" + source);
        }
        List<String> missing = publicSpotMapper.selectLiveExternalIds(source).stream()
                .filter(id -> !present.contains(id.strip()))
                .toList();
        if (missing.isEmpty()) {
            return 0;
        }
        return markRemoved(source, missing);
    }

    @Transactional(readOnly = true)
    public List<PublicSpotLocation> findLocations(PublicSpotSource source) {
        return publicSpotMapper.selectLocationsBySource(source).stream()
                .map(row -> new PublicSpotLocation(row.externalId(), row.name(), row.latitude(), row.longitude()))
                .toList();
    }

    private Map<String, NormalizedPublicSpot> normalize(PublicSpotSource source, List<PublicSpotCommand> commands) {
        Map<String, NormalizedPublicSpot> spots = new LinkedHashMap<>();
        for (PublicSpotCommand command : commands) {
            normalizeOrSkip(source, command).ifPresent(spot -> keepLast(source, spots, spot));
        }
        return spots;
    }

    private Optional<NormalizedPublicSpot> normalizeOrSkip(PublicSpotSource source, PublicSpotCommand command) {
        try {
            return Optional.of(NormalizedPublicSpot.from(command));
        } catch (IllegalArgumentException e) {
            log.warn(
                    "public spot skipped source={} externalId={} reason={}",
                    source,
                    command.externalId(),
                    e.getMessage());
            return Optional.empty();
        }
    }

    private void keepLast(PublicSpotSource source, Map<String, NormalizedPublicSpot> spots, NormalizedPublicSpot spot) {
        if (spots.put(spot.externalId(), spot) != null) {
            log.warn(
                    "public spot duplicated in batch, keeping last source={} externalId={}", source, spot.externalId());
        }
    }

    private Map<String, PublicSpotRow> findStored(PublicSpotSource source, Map<String, NormalizedPublicSpot> spots) {
        return publicSpotMapper.selectBySourceAndExternalIds(source, spots.keySet()).stream()
                .collect(Collectors.toMap(PublicSpotRow::externalId, Function.identity()));
    }

    // 저장된 장소가 없으면 IN 목록이 비어 문장이 깨지므로 조회하지 않는다.
    private Map<Long, Set<PublicSpotSetting>> findStoredSettings(Collection<PublicSpotRow> stored) {
        if (stored.isEmpty()) {
            return Map.of();
        }
        List<Long> spotIds = stored.stream().map(PublicSpotRow::spotId).toList();
        Map<Long, Set<PublicSpotSetting>> settings = new HashMap<>();
        for (PublicSpotSettingRow row : publicSpotMapper.selectSettings(spotIds)) {
            settings.computeIfAbsent(row.spotId(), id -> EnumSet.noneOf(PublicSpotSetting.class))
                    .add(row.setting());
        }
        return settings;
    }

    private void insert(PublicSpotSource source, List<PublicSpotInsertRow> inserts, Instant now) {
        if (inserts.isEmpty()) {
            return;
        }
        publicSpotMapper.insertSpots(source.spotType(), inserts, now);
        publicSpotMapper.insertDetails(source, inserts, now);
    }

    private void insertSettingsOfNewSpots(Map<PublicSpotInsertRow, Set<PublicSpotSetting>> settingsByInsert) {
        List<PublicSpotSettingRow> rows = new ArrayList<>();
        settingsByInsert.forEach((insert, settings) -> rows.addAll(settingRows(insert.getSpotId(), settings)));
        insertSettings(rows);
    }

    private void insertSettings(List<PublicSpotSettingRow> rows) {
        if (!rows.isEmpty()) {
            publicSpotMapper.insertSettings(rows);
        }
    }

    private static List<PublicSpotSettingRow> settingRows(long spotId, Set<PublicSpotSetting> settings) {
        return settings.stream()
                .map(setting -> new PublicSpotSettingRow(spotId, setting))
                .toList();
    }

    private void update(UpsertPlan plan, Instant now) {
        if (plan.detailUpdates.isEmpty()) {
            return;
        }
        publicSpotMapper.lockSpots(
                plan.detailUpdates.stream().map(PublicSpotUpdateRow::spotId).toList());
        if (!plan.spotUpdates.isEmpty()) {
            publicSpotMapper.updateSpots(plan.spotUpdates, now);
        }
        if (!plan.detailUpdates.isEmpty()) {
            publicSpotMapper.updateDetails(plan.detailUpdates, now);
        }
        if (!plan.settingReplacementIds.isEmpty()) {
            publicSpotMapper.deleteSettings(plan.settingReplacementIds);
            insertSettings(plan.settingReplacementRows);
        }
        if (!plan.restoredSpotIds.isEmpty()) {
            publicSpotMapper.restoreSpots(plan.restoredSpotIds, now);
            publicSpotMapper.clearSourceRemoved(plan.restoredSpotIds, now);
        }
    }

    private static int lengthOf(String value) {
        return value.codePointCount(0, value.length());
    }

    private PublicSpotColumns toColumns(NormalizedPublicSpot spot) {
        if (spot.facilities() == null) {
            return spot.toColumns(null);
        }
        return spot.toColumns(jsonMapper.writeValueAsString(spot.facilities()));
    }

    // MySQL은 JSON을 저장할 때 키 순서와 공백을 정규화한다. 그래서 저장된 문자열은 넘긴 문자열과 다를 수 있으므로 맵으로 바꿔서 비교한다.
    private Map<String, String> parseFacilities(String storedJson) {
        if (storedJson == null) {
            return null;
        }
        return jsonMapper.readValue(storedJson, FACILITIES_TYPE);
    }

    private final class UpsertPlan {

        private final Map<Long, Set<PublicSpotSetting>> storedSettings;
        private final List<PublicSpotInsertRow> inserts = new ArrayList<>();
        private final Map<PublicSpotInsertRow, Set<PublicSpotSetting>> insertedSettings = new LinkedHashMap<>();
        private final List<Long> settingReplacementIds = new ArrayList<>();
        private final List<PublicSpotSettingRow> settingReplacementRows = new ArrayList<>();
        private final List<PublicSpotUpdateRow> spotUpdates = new ArrayList<>();
        private final List<PublicSpotUpdateRow> detailUpdates = new ArrayList<>();
        private final List<Long> restoredSpotIds = new ArrayList<>();
        private int unchanged;

        UpsertPlan(Map<Long, Set<PublicSpotSetting>> storedSettings) {
            this.storedSettings = storedSettings;
        }

        void add(NormalizedPublicSpot spot, PublicSpotRow stored) {
            if (stored == null) {
                PublicSpotInsertRow insert = new PublicSpotInsertRow(toColumns(spot));
                inserts.add(insert);
                insertedSettings.put(insert, spot.settings());
                return;
            }
            boolean spotChanged = spot.differsInSpotColumns(stored);
            boolean settingsChanged = spot.differsInSettings(storedSettings.getOrDefault(stored.spotId(), Set.of()));
            boolean detailChanged =
                    settingsChanged || spot.differsInDetailColumns(stored, parseFacilities(stored.facilities()));
            if (!spotChanged && !detailChanged && !stored.sourceRemoved()) {
                unchanged++;
                return;
            }
            PublicSpotUpdateRow row = new PublicSpotUpdateRow(stored.spotId(), toColumns(spot));
            if (spotChanged) {
                spotUpdates.add(row);
            }
            detailUpdates.add(row);
            if (settingsChanged) {
                settingReplacementIds.add(stored.spotId());
                settingReplacementRows.addAll(settingRows(stored.spotId(), spot.settings()));
            }
            if (stored.sourceRemoved()) {
                restoredSpotIds.add(stored.spotId());
            }
        }
    }
}

package com.moewadeul.publicdata.application.gocamping;

import com.moewadeul.publicdata.infra.gocamping.GoCampingItem;
import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.application.PublicSpotSetting;
import com.moewadeul.spot.application.publicspot.PublicSpotCommand;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class GoCampingItemConverter {

    private static final Map<String, Function<GoCampingItem, String>> FACILITY_FIELDS = facilityFields();

    private static final String REMOVED_SYNC_STATUS = "D";

    private static final Map<String, PublicSpotOperatingStatus> OPERATING_STATUSES = Map.of(
            "운영", PublicSpotOperatingStatus.OPERATING,
            "휴장", PublicSpotOperatingStatus.TEMPORARILY_CLOSED,
            "폐업", PublicSpotOperatingStatus.PERMANENTLY_CLOSED);

    private static final Map<String, PublicSpotSetting> SETTINGS = Map.of(
            "산", PublicSpotSetting.MOUNTAIN,
            "숲", PublicSpotSetting.FOREST,
            "계곡", PublicSpotSetting.VALLEY,
            "해변", PublicSpotSetting.BEACH,
            "섬", PublicSpotSetting.ISLAND,
            "강", PublicSpotSetting.RIVER,
            "호수", PublicSpotSetting.LAKE,
            "도심", PublicSpotSetting.CITY);

    private GoCampingItemConverter() {}

    static Optional<PublicSpotCommand> convert(GoCampingItem item) {
        String externalId = blankToNull(item.contentId());
        String name = blankToNull(item.facltNm());
        if (externalId == null || name == null) {
            log.warn("gocamping item skipped: missing id or name contentId={}", externalId);
            return Optional.empty();
        }
        Double latitude = parseCoordinate(item.mapY());
        Double longitude = parseCoordinate(item.mapX());
        if (latitude == null || longitude == null) {
            log.warn("gocamping item skipped: unparseable coordinate contentId={}", externalId);
            return Optional.empty();
        }
        return Optional.of(new PublicSpotCommand(
                externalId,
                name,
                latitude,
                longitude,
                joinAddress(item.addr1(), item.addr2()),
                blankToNull(item.induty()),
                facilities(item),
                blankToNull(item.tel()),
                blankToNull(item.homepage()),
                operatingStatus(externalId, item.manageSttus()),
                parseDate(externalId, "hvofBgnde", item.hvofBgnde()),
                parseDate(externalId, "hvofEnddle", item.hvofEnddle()),
                parseDate(externalId, "modifiedtime", item.modifiedtime()),
                settings(externalId, item.lctCl())));
    }

    static boolean isRemoved(GoCampingItem item) {
        return REMOVED_SYNC_STATUS.equals(blankToNull(item.syncStatus()));
    }

    private static PublicSpotOperatingStatus operatingStatus(String externalId, String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        PublicSpotOperatingStatus status = OPERATING_STATUSES.get(trimmed);
        if (status == null) {
            log.warn("gocamping item has unknown operating status contentId={} manageSttus={}", externalId, trimmed);
        }
        return status;
    }

    private static Set<PublicSpotSetting> settings(String externalId, String value) {
        Set<PublicSpotSetting> settings = EnumSet.noneOf(PublicSpotSetting.class);
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return settings;
        }
        for (String part : trimmed.split(",")) {
            addSetting(settings, externalId, blankToNull(part));
        }
        return settings;
    }

    private static void addSetting(Set<PublicSpotSetting> settings, String externalId, String value) {
        if (value == null) {
            return;
        }
        PublicSpotSetting setting = SETTINGS.get(value);
        if (setting == null) {
            log.warn("gocamping item has unknown setting contentId={} lctCl={}", externalId, value);
            return;
        }
        settings.add(setting);
    }

    private static LocalDate parseDate(String externalId, String field, String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            log.warn("gocamping item has unparseable date contentId={} field={}", externalId, field);
            return null;
        }
    }

    private static Double parseCoordinate(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Double.valueOf(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String joinAddress(String addr1, String addr2) {
        String base = blankToNull(addr1);
        String detail = blankToNull(addr2);
        if (detail == null) {
            return base;
        }
        if (base == null) {
            return detail;
        }
        return base + " " + detail;
    }

    private static Map<String, String> facilities(GoCampingItem item) {
        Map<String, String> facilities = new LinkedHashMap<>();
        FACILITY_FIELDS.forEach((key, getter) -> {
            String value = blankToNull(getter.apply(item));
            if (value != null) {
                facilities.put(key, value);
            }
        });
        return facilities;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static Map<String, Function<GoCampingItem, String>> facilityFields() {
        Map<String, Function<GoCampingItem, String>> fields = new LinkedHashMap<>();
        fields.put("toiletCo", GoCampingItem::toiletCo);
        fields.put("swrmCo", GoCampingItem::swrmCo);
        fields.put("wtrplCo", GoCampingItem::wtrplCo);
        fields.put("brazierCl", GoCampingItem::brazierCl);
        fields.put("sbrsCl", GoCampingItem::sbrsCl);
        fields.put("sbrsEtc", GoCampingItem::sbrsEtc);
        fields.put("posblFcltyCl", GoCampingItem::posblFcltyCl);
        fields.put("posblFcltyEtc", GoCampingItem::posblFcltyEtc);
        fields.put("animalCmgCl", GoCampingItem::animalCmgCl);
        return Collections.unmodifiableMap(fields);
    }
}

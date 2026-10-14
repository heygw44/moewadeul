package com.moewadeul.publicdata.application.forest;

import com.moewadeul.publicdata.infra.forest.ForestStandardRecord;
import com.moewadeul.spot.application.publicspot.PublicSpotLocation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 전국휴양림표준데이터의 행을 휴양림 단위로 정리하고, 저장할 휴양림마다 외부 ID를 정한다. 파일과 DB를 읽지 않는 순수 계산이다.
 *
 * <p>이 파일에는 고유 ID가 없고, 도와 시·군이 같은 휴양림을 따로 제출해서 이름과 시도명이 다른 중복 행이 있다. 그래서 매처는 다음 순서로 정리한다.
 *
 * <ol>
 *   <li>숙박가능여부가 {@code Y}가 아닌 행과 위경도를 읽지 못하는 행을 뺀다.
 *   <li>서로 100m 안에 있는 행을 한 휴양림으로 묶는다. 대표 행은 데이터기준일자가 가장 늦은 행이고, 같으면 파일에서 먼저 나온 행이다.
 *   <li>국립자연휴양림과 겹치는 묶음을 뺀다. 묶음의 어느 행이든 국립 휴양림과 100m 안이거나, 이름 핵심이 같거나 서로 포함하고 1.5km 안이면 겹친다.
 *   <li>남은 묶음에 외부 ID를 정한다. 이미 저장한 표준데이터 장소와 위치로 맞춰 보고, 맞는 장소가 없으면 이름 핵심과 좌표로 새 ID를 만든다.
 * </ol>
 *
 * <p>외부 ID를 이름이 아니라 위치로 이어 붙이는 까닭은, 원천이 이름을 고치거나 좌표를 조금 바로잡아도 같은 장소 행을 유지하려는 것이다. 외부 ID가
 * 바뀌면 이전 장소는 숨겨지고 새 장소가 생겨서, 그 장소에 달린 후기와 베이스캠프가 끊긴다.
 */
public final class ForestStandardMatcher {

    static final double GROUPING_METERS = 100;
    static final double NATIONAL_NEAR_METERS = 100;
    static final double NATIONAL_NAME_METERS = 1_500;
    static final double EXISTING_NEAR_METERS = 100;
    static final double EXISTING_NAME_METERS = 5_000;
    static final int MIN_NAME_CORE_LENGTH = 2;
    static final String EXTERNAL_ID_PREFIX = "RF-";
    private static final int ID_HASH_HEX_LENGTH = 24;
    private static final String LODGING_AVAILABLE = "Y";
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private static final Pattern PARENTHESIZED = Pattern.compile("[(（][^)）]*([)）]|$)");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

    private ForestStandardMatcher() {}

    /**
     * 호출하면 records를 정리해서 저장할 휴양림과 건너뛴 수를 돌려준다.
     *
     * @param records 파일의 행
     * @param nationalLocations 이미 저장한 국립자연휴양림 장소(숨긴 장소 포함)
     * @param existingLocations 이미 저장한 표준데이터 장소(숨긴 장소 포함)
     */
    public static Result match(
            List<ForestStandardRecord> records,
            List<PublicSpotLocation> nationalLocations,
            List<PublicSpotLocation> existingLocations) {
        int skippedNoLodging = 0;
        List<Row> rows = new ArrayList<>();
        for (ForestStandardRecord record : records) {
            if (!LODGING_AVAILABLE.equals(record.lodgingAvailable().strip())) {
                skippedNoLodging++;
                continue;
            }
            Row.parse(record).ifPresent(rows::add);
        }
        int skippedInvalid = records.size() - skippedNoLodging - rows.size();

        List<Group> groups = group(rows);
        List<Group> loadable = new ArrayList<>();
        for (Group group : groups) {
            if (!overlapsNational(group, nationalLocations)) {
                loadable.add(group);
            }
        }
        List<Forest> forests = assignExternalIds(loadable, existingLocations);
        return new Result(forests, groups.size(), skippedNoLodging, skippedInvalid, groups.size() - loadable.size());
    }

    /** 괄호와 그 안의 내용, 공백, {@code 국립}을 지우고 {@code 자연휴양림}, {@code 휴양림}을 지운 이름. 이름 비교에만 쓴다. */
    static String nameCore(String name) {
        String core = PARENTHESIZED.matcher(name).replaceAll("");
        core = WHITESPACE.matcher(core).replaceAll("");
        return core.replace("국립", "").replace("자연휴양림", "").replace("휴양림", "");
    }

    // 묶기

    private static List<Group> group(List<Row> rows) {
        int[] parent = new int[rows.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < rows.size(); i++) {
            for (int j = i + 1; j < rows.size(); j++) {
                if (rows.get(i).distanceTo(rows.get(j)) <= GROUPING_METERS) {
                    union(parent, i, j);
                }
            }
        }
        return collectGroups(rows, parent);
    }

    private static int find(int[] parent, int index) {
        int root = index;
        while (parent[root] != root) {
            root = parent[root];
        }
        parent[index] = root;
        return root;
    }

    // 작은 번호를 뿌리로 삼는다. 그래야 묶음의 순서가 첫 행이 파일에 나온 순서와 같다.
    private static void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) {
            parent[Math.max(rootA, rootB)] = Math.min(rootA, rootB);
        }
    }

    private static List<Group> collectGroups(List<Row> rows, int[] parent) {
        List<List<Row>> members = new ArrayList<>();
        int[] groupIndexOfRoot = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            int root = find(parent, i);
            if (root == i) {
                groupIndexOfRoot[i] = members.size();
                members.add(new ArrayList<>());
            }
            members.get(groupIndexOfRoot[root]).add(rows.get(i));
        }
        return members.stream().map(Group::new).toList();
    }

    // 국립과 겹침

    private static boolean overlapsNational(Group group, List<PublicSpotLocation> nationalLocations) {
        for (PublicSpotLocation national : nationalLocations) {
            String nationalCore = nameCore(national.name());
            for (Row row : group.rows()) {
                double meters = row.distanceTo(national.latitude(), national.longitude());
                if (meters <= NATIONAL_NEAR_METERS
                        || (meters <= NATIONAL_NAME_METERS && coresOverlap(row.core(), nationalCore))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean coresOverlap(String a, String b) {
        if (a.length() < MIN_NAME_CORE_LENGTH || b.length() < MIN_NAME_CORE_LENGTH) {
            return false;
        }
        return a.contains(b) || b.contains(a);
    }

    // 외부 ID

    private static List<Forest> assignExternalIds(List<Group> groups, List<PublicSpotLocation> existingLocations) {
        String[] assigned = new String[groups.size()];
        Set<String> used = new HashSet<>();
        matchExisting(groups, existingLocations, assigned, used, EXISTING_NEAR_METERS, false);
        matchExisting(groups, existingLocations, assigned, used, EXISTING_NAME_METERS, true);

        Set<String> taken = new HashSet<>(used);
        existingLocations.forEach(existing -> taken.add(existing.externalId()));
        List<Forest> forests = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            Row representative = groups.get(i).representative();
            String externalId = assigned[i] != null ? assigned[i] : newExternalId(representative, taken);
            taken.add(externalId);
            forests.add(
                    new Forest(externalId, representative, groups.get(i).rows().size()));
        }
        return forests;
    }

    // 후보 쌍을 가까운 순으로 늘어놓고 앞에서부터 짝을 짓는다. 그래서 기존 장소 하나를 두 묶음이 다투면 더 가까운 묶음이 가져간다.
    private static void matchExisting(
            List<Group> groups,
            List<PublicSpotLocation> existingLocations,
            String[] assigned,
            Set<String> used,
            double maxMeters,
            boolean requireSameCore) {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            if (assigned[i] != null) {
                continue;
            }
            Row representative = groups.get(i).representative();
            for (PublicSpotLocation existing : existingLocations) {
                double meters = representative.distanceTo(existing.latitude(), existing.longitude());
                if (meters <= maxMeters && (!requireSameCore || sameCore(representative, existing))) {
                    candidates.add(new Candidate(i, existing.externalId(), meters));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::meters).thenComparingInt(Candidate::groupIndex));
        for (Candidate candidate : candidates) {
            if (assigned[candidate.groupIndex()] == null && used.add(candidate.externalId())) {
                assigned[candidate.groupIndex()] = candidate.externalId();
            }
        }
    }

    private static boolean sameCore(Row representative, PublicSpotLocation existing) {
        String existingCore = nameCore(existing.name());
        return !existingCore.isEmpty() && existingCore.equals(representative.core());
    }

    private static String newExternalId(Row representative, Set<String> taken) {
        String base = EXTERNAL_ID_PREFIX + hashPrefix(representative);
        String candidate = base;
        for (int suffix = 2; taken.contains(candidate); suffix++) {
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    private static String hashPrefix(Row representative) {
        String key = String.format(
                Locale.ROOT,
                "%s|%.4f|%.4f",
                representative.core(),
                representative.latitude(),
                representative.longitude());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return toHex(digest).substring(0, ID_HASH_HEX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 쓸 수 없습니다.", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    // 하버사인 공식으로 두 점 사이의 구면 거리(m)를 구한다.
    private static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng / 2)
                        * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /**
     * 매처의 결과.
     *
     * @param forests 저장할 휴양림. 묶음 하나가 휴양림 하나이고, 파일에 처음 나온 순서다.
     * @param groupCount 숙박 불가와 잘못된 행을 뺀 뒤 100m 묶기로 만든 묶음 수. 국립과 겹쳐 뺀 묶음도 센다.
     * @param skippedNoLodging 숙박가능여부가 {@code Y}가 아니라서 뺀 행 수
     * @param skippedInvalid 이름이 비었거나 위경도를 읽지 못해서 뺀 행 수
     * @param skippedNational 국립과 겹쳐서 뺀 묶음 수
     */
    public record Result(
            List<Forest> forests, int groupCount, int skippedNoLodging, int skippedInvalid, int skippedNational) {

        public Result {
            forests = List.copyOf(forests);
        }
    }

    /**
     * 저장할 휴양림 하나.
     *
     * @param externalId 정한 외부 ID
     * @param representative 묶음의 대표 행. 이름, 좌표, 주소, 연락처는 이 행의 값을 쓴다.
     * @param rowCount 이 휴양림으로 묶인 행 수
     */
    public record Forest(String externalId, Row representative, int rowCount) {}

    /**
     * 위경도를 읽은 파일의 한 행.
     *
     * @param record 파일에서 읽은 행
     * @param latitude 위도
     * @param longitude 경도
     * @param core 이름 핵심
     * @param sourceDate 데이터기준일자. 형식이 틀리면 null이다.
     */
    public record Row(
            ForestStandardRecord record, double latitude, double longitude, String core, LocalDate sourceDate) {

        static Optional<Row> parse(ForestStandardRecord record) {
            String name = record.name().strip();
            if (name.isEmpty()) {
                return Optional.empty();
            }
            try {
                double latitude = Double.parseDouble(record.latitude().strip());
                double longitude = Double.parseDouble(record.longitude().strip());
                if (!isCoordinate(latitude, longitude)) {
                    return Optional.empty();
                }
                return Optional.of(
                        new Row(record, latitude, longitude, nameCore(name), parseDate(record.sourceDate())));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        private static boolean isCoordinate(double latitude, double longitude) {
            return Double.isFinite(latitude)
                    && Double.isFinite(longitude)
                    && Math.abs(latitude) <= 90
                    && Math.abs(longitude) <= 180;
        }

        private static LocalDate parseDate(String text) {
            try {
                return LocalDate.parse(text.strip());
            } catch (DateTimeParseException e) {
                return null;
            }
        }

        double distanceTo(Row other) {
            return distanceTo(other.latitude, other.longitude);
        }

        double distanceTo(double otherLatitude, double otherLongitude) {
            return haversineMeters(latitude, longitude, otherLatitude, otherLongitude);
        }
    }

    private record Group(List<Row> rows) {

        Row representative() {
            Row best = rows.get(0);
            for (Row row : rows) {
                if (isLater(row.sourceDate(), best.sourceDate())) {
                    best = row;
                }
            }
            return best;
        }

        // 기준일을 읽지 못한 행은 가장 이른 것으로 본다. 같은 날짜면 먼저 나온 행이 대표로 남는다.
        private static boolean isLater(LocalDate date, LocalDate other) {
            if (date == null) {
                return false;
            }
            return other == null || date.isAfter(other);
        }
    }

    private record Candidate(int groupIndex, String externalId, double meters) {}
}

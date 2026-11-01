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

    static String nameCore(String name) {
        String core = PARENTHESIZED.matcher(name).replaceAll("");
        core = WHITESPACE.matcher(core).replaceAll("");
        return core.replace("국립", "").replace("자연휴양림", "").replace("휴양림", "");
    }

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

    public record Result(
            List<Forest> forests, int groupCount, int skippedNoLodging, int skippedInvalid, int skippedNational) {

        public Result {
            forests = List.copyOf(forests);
        }
    }

    public record Forest(String externalId, Row representative, int rowCount) {}

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

        private static boolean isLater(LocalDate date, LocalDate other) {
            if (date == null) {
                return false;
            }
            return other == null || date.isAfter(other);
        }
    }

    private record Candidate(int groupIndex, String externalId, double meters) {}
}

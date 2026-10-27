package com.moewadeul.publicdata.infra.park;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ParkBoundaryFileReader {

    private static final Pattern SOURCE_DATE_SUFFIX = Pattern.compile("_(\\d{8})\\.tsv$");
    private static final String BOM = "\uFEFF";
    private static final String HEADER = "name\tarea_type\twkt";
    private static final String DELIMITER = "\t";
    private static final int COLUMN_COUNT = 3;
    private static final int MAX_NAME_LENGTH = 100;
    private static final Set<String> AREA_TYPES = Set.of("NATIONAL_PARK", "PROVINCIAL_PARK", "COUNTY_PARK");

    public ParkBoundaryFile read(Path file) {
        LocalDate sourceDate = parseSourceDate(file);
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new ParkBoundaryFile(sourceDate, readRecords(reader, fileName(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("공원 경계 파일을 읽지 못했습니다. file=" + file, e);
        }
    }

    private static LocalDate parseSourceDate(Path file) {
        String fileName = fileName(file);
        Matcher matcher = SOURCE_DATE_SUFFIX.matcher(fileName);
        if (!matcher.find()) {
            throw new IllegalStateException("공원 경계 파일 이름이 기준일(_YYYYMMDD.tsv)로 끝나지 않습니다. file=" + fileName);
        }
        try {
            return LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("공원 경계 파일 이름의 기준일이 올바른 날짜가 아닙니다. file=" + fileName, e);
        }
    }

    private static String fileName(Path file) {
        Path fileName = file.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    private static List<ParkBoundaryRecord> readRecords(BufferedReader reader, String fileName) throws IOException {
        requireHeader(reader.readLine(), fileName);
        LineParser parser = new LineParser(fileName);
        List<ParkBoundaryRecord> records = new ArrayList<>();
        int lineNumber = 1;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (!line.isBlank()) {
                records.add(parser.parse(line, lineNumber));
            }
        }
        return records;
    }

    private static void requireHeader(String headerLine, String fileName) {
        if (headerLine == null) {
            throw new IllegalStateException("공원 경계 파일이 비어 있습니다. file=" + fileName);
        }
        String header = headerLine.startsWith(BOM) ? headerLine.substring(BOM.length()) : headerLine;
        if (!HEADER.equals(header)) {
            throw new IllegalStateException(
                    "공원 경계 파일의 헤더가 name, area_type, wkt 세 열을 탭으로 나눈 줄이 아닙니다. file=" + fileName + ", line=1");
        }
    }

    private static final class LineParser {

        private final String fileName;
        private final ParkBoundaryWkt boundaryWkt = new ParkBoundaryWkt();
        private final Set<AreaKey> seenKeys = new HashSet<>();

        LineParser(String fileName) {
            this.fileName = fileName;
        }

        ParkBoundaryRecord parse(String line, int lineNumber) {
            String[] values = line.split(DELIMITER, -1);
            if (values.length != COLUMN_COUNT) {
                throw reject(lineNumber, "열 수가 " + COLUMN_COUNT + "개가 아닙니다. actual=" + values.length);
            }
            String name = requireName(values[0], lineNumber);
            String areaType = requireAreaType(values[1], lineNumber);
            requireFirstOccurrence(new AreaKey(areaType, name), lineNumber);
            return new ParkBoundaryRecord(name, areaType, toMultiPolygonWkt(values[2], lineNumber));
        }

        private String requireName(String value, int lineNumber) {
            String name = value.strip();
            if (name.isEmpty()) {
                throw reject(lineNumber, "공원 이름이 비어 있습니다.");
            }
            if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
                throw reject(lineNumber, "공원 이름이 " + MAX_NAME_LENGTH + "자를 넘습니다.");
            }
            return name;
        }

        private String requireAreaType(String value, int lineNumber) {
            if (!AREA_TYPES.contains(value)) {
                throw reject(
                        lineNumber,
                        "area_type은 NATIONAL_PARK, PROVINCIAL_PARK, COUNTY_PARK 중 하나여야 합니다. actual=" + value);
            }
            return value;
        }

        private void requireFirstOccurrence(AreaKey key, int lineNumber) {
            if (!seenKeys.add(key)) {
                throw reject(
                        lineNumber, "같은 area_type과 이름이 앞에서 나왔습니다. areaType=" + key.areaType() + ", name=" + key.name());
            }
        }

        private String toMultiPolygonWkt(String wkt, int lineNumber) {
            try {
                return boundaryWkt.toMultiPolygonWkt(wkt);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(message(lineNumber, "경계 WKT가 잘못됐습니다. " + e.getMessage()), e);
            }
        }

        private IllegalStateException reject(int lineNumber, String reason) {
            return new IllegalStateException(message(lineNumber, reason));
        }

        private String message(int lineNumber, String reason) {
            return "공원 경계 파일 줄의 형식이 잘못됐습니다. " + reason + " file=" + fileName + ", line=" + lineNumber;
        }
    }

    private record AreaKey(String areaType, String name) {}
}

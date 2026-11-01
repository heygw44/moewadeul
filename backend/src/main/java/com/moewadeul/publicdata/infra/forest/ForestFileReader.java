package com.moewadeul.publicdata.infra.forest;

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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ForestFileReader {

    private static final Pattern SOURCE_DATE_SUFFIX = Pattern.compile("_(\\d{8})\\.csv$");
    // 공공데이터포털 파일은 UTF-8 BOM으로 시작하고, 기준일은 파일 안이 아니라 포털이 붙인 이름 끝의 _YYYYMMDD.csv에만 있다.
    private static final String BOM = "\uFEFF";
    private static final String DELIMITER = ",";
    private static final String QUOTE = "\"";

    private static final String INSTITUTION_ID_COLUMN = "기관아이디";
    private static final String NAME_COLUMN = "기관명";
    private static final String LATITUDE_COLUMN = "위도";
    private static final String LONGITUDE_COLUMN = "경도";
    private static final String PHONE_COLUMN = "연락처";
    private static final String HOMEPAGE_COLUMN = "홈페이지주소";

    public ForestFile read(Path file) {
        LocalDate sourceDate = parseSourceDate(file);
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new ForestFile(sourceDate, readRecords(reader, fileName(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("휴양림 파일을 읽지 못했습니다. file=" + file, e);
        }
    }

    private static LocalDate parseSourceDate(Path file) {
        String fileName = fileName(file);
        Matcher matcher = SOURCE_DATE_SUFFIX.matcher(fileName);
        if (!matcher.find()) {
            throw new IllegalStateException("휴양림 파일 이름이 기준일(_YYYYMMDD.csv)로 끝나지 않습니다. file=" + fileName);
        }
        try {
            return LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("휴양림 파일 이름의 기준일이 올바른 날짜가 아닙니다. file=" + fileName, e);
        }
    }

    private static String fileName(Path file) {
        Path fileName = file.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    private static List<ForestRecord> readRecords(BufferedReader reader, String fileName) throws IOException {
        String headerLine = reader.readLine();
        if (headerLine == null) {
            throw new IllegalStateException("휴양림 파일이 비어 있습니다. file=" + fileName);
        }
        Header header = Header.parse(stripBom(headerLine), fileName);
        List<ForestRecord> records = new ArrayList<>();
        int lineNumber = 1;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (!line.isBlank()) {
                records.add(header.toRecord(splitDataLine(line, header, fileName, lineNumber)));
            }
        }
        return records;
    }

    private static String stripBom(String line) {
        return line.startsWith(BOM) ? line.substring(BOM.length()) : line;
    }

    private static String[] splitDataLine(String line, Header header, String fileName, int lineNumber) {
        if (line.contains(QUOTE)) {
            throw new IllegalStateException("휴양림 파일에 따옴표가 든 줄이 있어 읽지 않습니다. file=" + fileName + ", line=" + lineNumber);
        }
        String[] values = line.split(DELIMITER, -1);
        if (values.length != header.columnCount()) {
            throw new IllegalStateException("휴양림 파일 줄의 열 수가 헤더와 다릅니다. file=" + fileName + ", line=" + lineNumber
                    + ", expected=" + header.columnCount() + ", actual=" + values.length);
        }
        return values;
    }

    private record Header(
            int columnCount, int institutionId, int name, int latitude, int longitude, int phone, int homepage) {

        static Header parse(String headerLine, String fileName) {
            List<String> columns = Arrays.stream(headerLine.split(DELIMITER, -1))
                    .map(String::strip)
                    .toList();
            return new Header(
                    columns.size(),
                    indexOf(columns, INSTITUTION_ID_COLUMN, fileName),
                    indexOf(columns, NAME_COLUMN, fileName),
                    indexOf(columns, LATITUDE_COLUMN, fileName),
                    indexOf(columns, LONGITUDE_COLUMN, fileName),
                    indexOf(columns, PHONE_COLUMN, fileName),
                    indexOf(columns, HOMEPAGE_COLUMN, fileName));
        }

        private static int indexOf(List<String> columns, String column, String fileName) {
            int index = columns.indexOf(column);
            if (index < 0) {
                throw new IllegalStateException("휴양림 파일 헤더에 필요한 열이 없습니다. file=" + fileName + ", column=" + column);
            }
            return index;
        }

        ForestRecord toRecord(String[] values) {
            return new ForestRecord(
                    values[institutionId],
                    values[name],
                    values[latitude],
                    values[longitude],
                    values[phone],
                    values[homepage]);
        }
    }
}

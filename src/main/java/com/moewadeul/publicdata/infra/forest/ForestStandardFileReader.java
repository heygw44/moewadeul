package com.moewadeul.publicdata.infra.forest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ForestStandardFileReader {

    private static final String RECORDS_KEY = "records";
    private static final String NAME_KEY = "휴양림명";
    private static final String PROVINCE_KEY = "시도명";
    private static final String LODGING_KEY = "숙박가능여부";
    private static final String ADDRESS_KEY = "소재지도로명주소";
    private static final String PHONE_KEY = "휴양림전화번호";
    private static final String HOMEPAGE_KEY = "홈페이지주소";
    private static final String LATITUDE_KEY = "위도";
    private static final String LONGITUDE_KEY = "경도";
    private static final String SOURCE_DATE_KEY = "데이터기준일자";

    private final JsonMapper jsonMapper;

    public ForestStandardFile read(Path file) {
        String fileName = fileName(file);
        try (InputStream in = Files.newInputStream(file)) {
            return new ForestStandardFile(readRecords(jsonMapper.readTree(in), fileName));
        } catch (IOException e) {
            throw new UncheckedIOException("휴양림 표준데이터 파일을 읽지 못했습니다. file=" + file, e);
        } catch (JacksonException e) {
            throw new IllegalStateException("휴양림 표준데이터 파일이 올바른 JSON이 아닙니다. file=" + fileName, e);
        }
    }

    private static String fileName(Path file) {
        Path fileName = file.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    private static List<ForestStandardRecord> readRecords(JsonNode root, String fileName) {
        JsonNode rows = root.get(RECORDS_KEY);
        if (rows == null || !rows.isArray()) {
            throw new IllegalStateException("휴양림 표준데이터 파일에 records 배열이 없습니다. file=" + fileName);
        }
        List<ForestStandardRecord> records = new ArrayList<>();
        int index = 0;
        for (JsonNode row : rows) {
            records.add(toRecord(row, fileName, index++));
        }
        return records;
    }

    private static ForestStandardRecord toRecord(JsonNode row, String fileName, int index) {
        if (!row.isObject()) {
            throw new IllegalStateException("휴양림 표준데이터 파일의 행이 객체가 아닙니다. file=" + fileName + ", index=" + index);
        }
        return new ForestStandardRecord(
                required(row, NAME_KEY, fileName, index),
                optional(row, PROVINCE_KEY),
                required(row, LODGING_KEY, fileName, index),
                required(row, ADDRESS_KEY, fileName, index),
                required(row, PHONE_KEY, fileName, index),
                required(row, HOMEPAGE_KEY, fileName, index),
                required(row, LATITUDE_KEY, fileName, index),
                required(row, LONGITUDE_KEY, fileName, index),
                required(row, SOURCE_DATE_KEY, fileName, index));
    }

    private static String required(JsonNode row, String key, String fileName, int index) {
        if (!row.has(key)) {
            throw new IllegalStateException(
                    "휴양림 표준데이터 파일의 행에 필요한 키가 없습니다. file=" + fileName + ", index=" + index + ", key=" + key);
        }
        return optional(row, key);
    }

    private static String optional(JsonNode row, String key) {
        JsonNode value = row.get(key);
        return value == null || value.isNull() ? "" : value.asString();
    }
}

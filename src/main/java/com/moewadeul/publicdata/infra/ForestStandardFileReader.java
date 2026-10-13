package com.moewadeul.publicdata.infra;

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

/**
 * 공공데이터포털에서 받은 전국휴양림표준데이터 JSON 파일을 읽는다.
 *
 * <p>파일은 UTF-8이고 {@code {"fields":[...],"records":[{"휴양림명":"...", ...}]}} 모양이다. 리더는 {@code records}의 각 행에서 키 이름으로
 * 값을 찾으므로, 키 순서가 바뀌거나 쓰지 않는 키가 늘어도 그대로 읽는다. 값이 문자열이든 숫자든 문자열로 바꿔 돌려주고, null은 빈
 * 문자열로 바꾼다.
 *
 * <p>이 파일은 사람이 내려받아 넣는 정적 파일이다. {@code records}가 없거나 한 행이라도 필요한 키가 빠졌으면 파일 전체가 잘못 받아졌을 수
 * 있으므로, 리더는 일부 행만 돌려주지 않고 파일 전체를 거부한다.
 */
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

    /**
     * 호출하면 파일의 {@code records}를 모두 읽어 돌려준다.
     *
     * @throws IllegalStateException JSON 형식이 아니거나, {@code records} 배열이 없거나, 행이 객체가 아니거나, 행에 필요한 키가 없을 때
     * @throws UncheckedIOException 파일이 없거나 읽지 못했을 때
     */
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

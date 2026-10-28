package com.moewadeul.publicdata.infra.forest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class ForestStandardFileReaderTest {

    private static final String FORM = """
            {"fields":[{"id":"휴양림명"}],"records":[%s]}""";
    private static final String FULL_RECORD = """
            {"휴양림명":"가나 자연휴양림","시도명":"경기도","숙박가능여부":"Y","소재지도로명주소":"경기도 가나시 숲길 1",
             "휴양림전화번호":"031-111-1111","홈페이지주소":"https://example.com/a","위도":"37.5","경도":"127.5",
             "데이터기준일자":"2026-09-01","입장료":"무료"}""";

    private final ForestStandardFileReader reader =
            new ForestStandardFileReader(JsonMapper.builder().build());

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("[F-06] records의 각 행에서 필요한 키를 문자열 그대로 읽고, 쓰지 않는 키는 무시한다")
    void readsRequiredKeysAsStrings() throws IOException {
        // given
        Path file = write(FORM.formatted(FULL_RECORD));

        // when
        ForestStandardFile result = reader.read(file);

        // then
        assertThat(result.records())
                .containsExactly(new ForestStandardRecord(
                        "가나 자연휴양림",
                        "경기도",
                        "Y",
                        "경기도 가나시 숲길 1",
                        "031-111-1111",
                        "https://example.com/a",
                        "37.5",
                        "127.5",
                        "2026-09-01"));
    }

    @Test
    @DisplayName("[F-06] 값이 숫자이면 글자 표현으로, null이면 빈 문자열로 읽는다")
    void readsNumbersAndNullsAsText() throws IOException {
        // given
        Path file = write(FORM.formatted("""
                {"휴양림명":"가나 자연휴양림","숙박가능여부":"Y","소재지도로명주소":null,"휴양림전화번호":null,
                 "홈페이지주소":"","위도":37.5,"경도":127.25,"데이터기준일자":"2026-09-01"}"""));

        // when
        ForestStandardRecord record = reader.read(file).records().get(0);

        // then
        assertThat(record.latitude()).isEqualTo("37.5");
        assertThat(record.longitude()).isEqualTo("127.25");
        assertThat(record.address()).isEmpty();
        assertThat(record.phone()).isEmpty();
        assertThat(record.homepage()).isEmpty();
        assertThat(record.province()).isEmpty();
    }

    @Test
    @DisplayName("[F-06] records가 빈 배열이면 행이 없는 결과를 돌려준다")
    void readsEmptyRecords() throws IOException {
        // given
        Path file = write(FORM.formatted(""));

        // when, then
        assertThat(reader.read(file).records()).isEmpty();
    }

    @Test
    @DisplayName("[F-06] records 배열이 없으면 파일 전체를 거부한다")
    void rejectsFileWithoutRecords() throws IOException {
        // given
        Path file = write("{\"fields\":[]}");

        // when, then
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("records");
    }

    @Test
    @DisplayName("[F-06] 한 행이라도 필요한 키가 없으면 일부만 돌려주지 않고 파일 전체를 거부한다")
    void rejectsFileWhenRowMissesRequiredKey() throws IOException {
        // given
        String withoutLatitude = FULL_RECORD.replace("\"위도\":\"37.5\",", "");
        Path file = write(FORM.formatted(FULL_RECORD + "," + withoutLatitude));

        // when, then
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("index=1")
                .hasMessageContaining("위도");
    }

    @Test
    @DisplayName("[F-06] JSON 형식이 아닌 파일은 거부한다")
    void rejectsMalformedJson() throws IOException {
        // given
        Path file = write("{\"records\": [");

        // when, then
        assertThatThrownBy(() -> reader.read(file)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("[F-06] 파일이 없으면 UncheckedIOException을 던진다")
    void throwsUncheckedIoExceptionWhenFileIsMissing() {
        // given
        Path file = tempDir.resolve("missing.json");

        // when, then
        assertThatThrownBy(() -> reader.read(file)).isInstanceOf(UncheckedIOException.class);
    }

    private Path write(String content) throws IOException {
        return Files.writeString(tempDir.resolve("forest-standard.json"), content, StandardCharsets.UTF_8);
    }
}

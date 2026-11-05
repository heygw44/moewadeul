package com.moewadeul.common.logging;

import com.moewadeul.common.error.UniqueViolations;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.json.JsonWriter.ValueProcessor;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/** MySQL은 유니크 제약을 어긴 값을 오류 메시지에 담는다. 스택 트레이스의 원인 메시지까지 어느 로거에서 나오든 내보내기 전에 지운다. */
public class ConstraintValueRedactingLogCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(ValueProcessor.of(String.class, UniqueViolations::redactValues));
    }
}

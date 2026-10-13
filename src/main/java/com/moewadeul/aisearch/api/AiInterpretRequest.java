package com.moewadeul.aisearch.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record AiInterpretRequest(
        @Schema(description = "장소를 찾는 문장. 앞뒤 공백을 뺀 길이가 2~100자여야 한다.") @ValidAiQuery
        String query) {

    // record가 만드는 기본 toString은 문장을 그대로 출력한다. 누군가 요청 객체를 로그에 찍어도 문장이 남지 않게 가린다.
    @Override
    public String toString() {
        return "AiInterpretRequest[query=****]";
    }
}

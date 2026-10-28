package com.moewadeul.aisearch.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record AiInterpretRequest(
        @Schema(description = "장소를 찾는 문장. 앞뒤 공백을 뺀 길이가 2~100자여야 한다.") @ValidAiQuery
        String query) {

    @Override
    public String toString() {
        return "AiInterpretRequest[query=****]";
    }
}

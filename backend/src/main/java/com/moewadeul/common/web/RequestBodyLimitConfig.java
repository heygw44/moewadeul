package com.moewadeul.common.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class RequestBodyLimitConfig {

    static final int MAX_DOCUMENT_LENGTH = 1_000_000;
    static final int MAX_STRING_LENGTH = 100_000;

    // 상한이 없으면 비밀번호가 6천만 자인 로그인 요청 한 건으로 힙이 다 차서 앱이 종료됐다(#863). Caddy의 1MB 상한을 거치지 않는 요청도 막는다.
    // 문자열 상한은 가장 긴 정상 값(커뮤니티 글 10,000자)의 10배다. 문서 상한만 두면 99만 자 문자열 50건 동시 요청에 힙이 248MB까지 찼다.
    // 공유 JsonFactory에 걸면 외부 API 응답(고캠핑 목록 등)을 읽는 코드까지 막히므로, 요청 본문을 읽는 서버 쪽 JSON 컨버터에만 따로 만든 매퍼를 준다.
    // Boot가 공유 매퍼로 만든 컨버터(@Order(0))를 이 빈이 순서상 뒤에서 바꿔 끼운다.
    @Bean
    ServerHttpMessageConvertersCustomizer requestBodyLimitedJsonConverter(
            JsonFactory jsonFactory, ObjectProvider<JsonMapperBuilderCustomizer> customizers) {
        JsonFactory limited = jsonFactory
                .rebuild()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxDocumentLength(MAX_DOCUMENT_LENGTH)
                        .maxStringLength(MAX_STRING_LENGTH)
                        .build())
                .build();
        JsonMapper.Builder builder = JsonMapper.builder(limited);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        JsonMapper mapper = builder.build();
        return converters -> converters.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper));
    }
}

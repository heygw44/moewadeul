package com.moewadeul.aisearch.application;

/**
 * 한국어 문장 하나를 장소 검색 조건으로 바꾸는 포트.
 *
 * <p>조건만 뽑고 장소를 고르거나 합법 여부를 판단하지 않는다. 이 포트 뒤에 다른 제공자나 의미 검색 단계를 끼워 넣어도 호출하는 쪽은 바뀌지 않는다.
 */
public interface SpotQueryInterpreter {

    /**
     * @param query 사용자가 쓴 문장
     * @throws SpotQueryInterpretationException 호출이나 응답 검증이 실패했을 때. 재시도하지 않는다.
     */
    SpotQueryInterpretation interpret(String query);
}

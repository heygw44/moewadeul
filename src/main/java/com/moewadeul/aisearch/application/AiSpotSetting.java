package com.moewadeul.aisearch.application;

/**
 * 문장에서 읽어 낸 장소의 입지(주변 환경).
 *
 * <p>spot 모듈의 입지와 값이 같지만 일부러 따로 둔다. 이 모듈은 common에만 의존해야 해서 spot의 enum을 가져다 쓸 수 없다. 두 값을 맞추는 일은
 * 이 결과를 받아 조회에 넘기는 쪽이 맡는다.
 */
public enum AiSpotSetting {
    MOUNTAIN,
    FOREST,
    VALLEY,
    BEACH,
    ISLAND,
    RIVER,
    LAKE,
    CITY
}

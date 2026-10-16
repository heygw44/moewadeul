package com.moewadeul.spot.application;

/** 공원 경계 경고에 붙이는 안내 문구. 박지 제보 응답과 장소 상세 응답이 같은 문구를 쓴다. */
public final class ParkWarningTexts {

    /**
     * 경고 박지에 붙이는 안내. 공원 안에서 야영해도 뒷정리만 잘하면 된다고 읽히지 않게, 흔적 남기지 않기가 아니라 야영하지 말라는 안내를 준다.
     * 경고가 아닌 박지에는 {@link #NORMAL_GUIDE}를 쓴다.
     */
    public static final String WARNED_GUIDE =
            "자연공원 안에서는 지정된 야영장 밖의 야영과 취사가 금지돼 있고 어기면 과태료 대상이에요. 이곳에서는 야영하지 말고 가까운 지정 야영장을 찾아 주세요.";

    public static final String NORMAL_GUIDE = "머문 자리에 흔적을 남기지 마세요. 쓰레기는 모두 가져가 주세요.";

    /**
     * 경고가 아닌 박지에 붙이는 확인 안내. 서버는 공원 경계만 판정하고 보호구역, 입산 통제, 사유지 여부는 확인하지 않는다. 그래서 경고가 없다고 야영해도
     * 되는 곳으로 읽히지 않게, 관할 기관에 직접 물어보라고 안내하고 서비스는 안내만 한다고 밝힌다. 경고 박지에는 야영하지 말라는 {@link #WARNED_GUIDE}와 뜻이
     * 부딪쳐서 쓰지 않는다.
     */
    public static final String BAKJI_CHECK_NOTICE =
            "뫼와들은 이곳에서 야영해도 되는지 확인하지 않았어요. 가기 전에 관할 시군청 산림과나 국유림관리소, 공원 사무소에 야영할 수 있는지 물어봐 주세요."
                    + " 뫼와들은 제보된 정보를 보여 줄 뿐이고, 야영 중에 생긴 일에는 책임지지 않아요.";

    /** 경고 여부에 맞는 박지 확인 안내. 경고 박지이면 {@code null}이다. */
    public static String bakjiCheckNoticeOf(boolean warned) {
        return warned ? null : BAKJI_CHECK_NOTICE;
    }

    /** 경계 데이터는 참고용이라서, 장소 상세와 박지 제보 응답은 경고와 함께 이 고지를 준다. */
    public static final String NOTICE = "공원 경계는 참고 자료예요. 정확한 경계는 공식 고시 도면을 확인해 주세요.";

    private ParkWarningTexts() {}
}

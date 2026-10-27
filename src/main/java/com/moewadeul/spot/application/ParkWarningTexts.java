package com.moewadeul.spot.application;

public final class ParkWarningTexts {

    public static final String WARNED_GUIDE =
            "자연공원 안에서는 지정된 야영장 밖의 야영과 취사가 금지돼 있고 어기면 과태료 대상이에요. 이곳에서는 야영하지 말고 가까운 지정 야영장을 찾아 주세요.";

    public static final String NORMAL_GUIDE = "머문 자리에 흔적을 남기지 마세요. 쓰레기는 모두 가져가 주세요.";

    public static final String BAKJI_CHECK_NOTICE =
            "뫼와들은 이곳에서 야영해도 되는지 확인하지 않았어요. 가기 전에 관할 시군청 산림과나 국유림관리소, 공원 사무소에 야영할 수 있는지 물어봐 주세요."
                    + " 뫼와들은 제보된 정보를 보여 줄 뿐이고, 야영 중에 생긴 일에는 책임지지 않아요.";

    public static String bakjiCheckNoticeOf(boolean warned) {
        return warned ? null : BAKJI_CHECK_NOTICE;
    }

    public static final String NOTICE = "공원 경계는 참고 자료예요. 정확한 경계는 공식 고시 도면을 확인해 주세요.";

    private ParkWarningTexts() {}
}

package com.moewadeul.notification.application.event;

// bulkEmail은 작업 하나가 수백 통을 만드는 메일이다. 이 메일이 밀려도 회원 한 명의 행동으로 생긴 메일이 그 뒤에 서지 않도록 나중에 보낸다.
record NotificationDraft(long memberId, String type, String title, String body, String link, boolean bulkEmail) {

    NotificationDraft(long memberId, String type, String title, String body, String link) {
        this(memberId, type, title, body, link, false);
    }
}

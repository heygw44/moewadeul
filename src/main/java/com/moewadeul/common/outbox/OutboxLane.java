package com.moewadeul.common.outbox;

/** 메일 SMTP처럼 느려질 수 있는 처리기는 MAIL 줄로 떼어, 느린 처리기가 다른 이벤트를 막지 않게 한다. */
public enum OutboxLane {
    MAIL,
    DEFAULT
}

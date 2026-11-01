package com.moewadeul.publicdata.application;

public class SyncJobStoppedException extends RuntimeException {

    public SyncJobStoppedException() {
        super("서버가 종료 중이라 진행 위치를 남기고 작업을 멈췄습니다.");
    }
}

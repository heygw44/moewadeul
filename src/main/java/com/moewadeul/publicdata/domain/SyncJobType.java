package com.moewadeul.publicdata.domain;

/** 공공데이터 적재·동기화 작업의 종류. sync_job_run.job_type에 이름 그대로 저장한다. 장소 상세가 마지막 확인 시각을 찾을 때 public_spot_detail.source와 이름을 맞춰 잇기 때문에, 적재 작업의 이름은 그 작업이 쓰는 출처 이름과 같아야 한다. */
public enum SyncJobType {
    GOCAMPING,
    FOREST,
    FOREST_STANDARD,
    PARK_BOUNDARY,
    BAKJI_REJUDGE
}

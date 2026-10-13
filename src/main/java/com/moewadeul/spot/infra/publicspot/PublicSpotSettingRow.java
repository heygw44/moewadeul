package com.moewadeul.spot.infra.publicspot;

import com.moewadeul.spot.application.PublicSpotSetting;

/** 공공데이터 야영장 한 곳의 입지 하나. MyBatis가 읽을 때와 추가할 때 모두 쓴다. */
public record PublicSpotSettingRow(long spotId, PublicSpotSetting setting) {}

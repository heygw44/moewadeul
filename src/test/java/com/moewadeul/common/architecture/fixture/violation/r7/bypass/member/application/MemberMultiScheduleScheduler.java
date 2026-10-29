package com.moewadeul.common.architecture.fixture.violation.r7.bypass.member.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;

public class MemberMultiScheduleScheduler {

    @Schedules({@Scheduled(fixedDelay = 1000), @Scheduled(cron = "0 0 5 * * *")})
    public void cleanUp() {}
}

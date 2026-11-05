package com.moewadeul.common.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.time.KoreaTime;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

class ScheduledJobCronContractTest {

    private static final String DAILY = "daily";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^:}]+:([^}]+)}");
    private static final ZonedDateTime FROM =
            LocalDateTime.of(2026, 11, 5, 23, 59, 59).atZone(ZoneId.of(KoreaTime.ZONE_ID));

    @Test
    @DisplayName("[NFR-09] @Scheduled 크론 작업은 모두 기본 표현식이 유효하고 한국 시간으로 돌며, 자정 작업은 정해 둔 시각과 주기를 지킨다")
    void cronJobsRunAtExpectedKoreanTimeAndInterval() throws ClassNotFoundException {
        Map<String, String> schedules = new TreeMap<>();
        for (Scheduled scheduled : scheduledCronMethods()) {
            assertThat(scheduled.zone()).isEqualTo(KoreaTime.ZONE_ID);
            String expression = defaultValue(scheduled.cron());
            CronExpression cron = CronExpression.parse(expression);
            ZonedDateTime first = cron.next(FROM);
            assertThat(first).as(expression).isNotNull();
            ZonedDateTime second = cron.next(first);
            ZonedDateTime third = cron.next(second);
            schedules.merge(scheduled.cron().replaceAll("^\\$\\{|:.*$", ""), describe(first, second, third), (a, b) -> a);
        }

        assertThat(schedules)
                .containsOnly(
                        Map.entry("moewadeul.aisearch.usage-cleanup.cron", DAILY),
                        Map.entry("moewadeul.basecamp.auto-transition.cron", "00:00 PT10M"),
                        Map.entry("moewadeul.basecamp.contact-cleanup.cron", DAILY),
                        Map.entry("moewadeul.idempotency.cleanup.cron", DAILY),
                        Map.entry("moewadeul.member.email-verification-cleanup.cron", DAILY),
                        Map.entry("moewadeul.member.login-history-cleanup.cron", DAILY),
                        Map.entry("moewadeul.member.reset-throttle-cleanup.cron", DAILY),
                        Map.entry("moewadeul.member.reset-token-cleanup.cron", DAILY),
                        Map.entry("moewadeul.member.unverified-cleanup.cron", DAILY),
                        Map.entry("moewadeul.moderation.report-content-cleanup.cron", DAILY),
                        Map.entry("moewadeul.outbox.cleanup.cron", DAILY),
                        Map.entry("moewadeul.program.payment-expiry.cron", "00:00 PT1M"),
                        Map.entry("moewadeul.publicdata.gocamping.sync.cron", DAILY),
                        Map.entry("moewadeul.trust.ci-retention-cleanup.cron", DAILY),
                        Map.entry("moewadeul.trust.report-content-cleanup.cron", DAILY),
                        Map.entry("moewadeul.trust.sanction-expiry.cron", "00:00 PT10M"));
    }

    private static String describe(ZonedDateTime first, ZonedDateTime second, ZonedDateTime third) {
        Duration gap = Duration.between(first, second);
        assertThat(Duration.between(second, third)).isEqualTo(gap);
        return gap.equals(Duration.ofDays(1)) ? DAILY : first.toLocalTime() + " " + gap;
    }

    private static String defaultValue(String cron) {
        Matcher matcher = PLACEHOLDER.matcher(cron);
        assertThat(matcher.matches()).as(cron).isTrue();
        return matcher.group(1);
    }

    private static List<Scheduled> scheduledCronMethods() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        List<Scheduled> found = new java.util.ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.moewadeul")) {
            for (Method method : Class.forName(definition.getBeanClassName()).getDeclaredMethods()) {
                Scheduled scheduled = AnnotatedElementUtils.findMergedAnnotation(method, Scheduled.class);
                if (scheduled != null && !scheduled.cron().isEmpty()) {
                    found.add(scheduled);
                }
            }
        }
        return found;
    }
}

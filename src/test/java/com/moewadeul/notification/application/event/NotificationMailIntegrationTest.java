package com.moewadeul.notification.application.event;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.mail.MailMessage;
import com.moewadeul.common.mail.MailSender;
import com.moewadeul.common.mail.TestMailSender;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.common.outbox.OutboxLane;
import com.moewadeul.common.scheduling.ShutdownSignal;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.common.web.WebProperties;
import com.moewadeul.member.domain.Member;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.notification.application.NotificationEventTypes;
import com.moewadeul.notification.infra.event.NotificationMailMapper;
import com.moewadeul.outbox.application.OutboxPublisher;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class NotificationMailIntegrationTest {

    private static final String AGGREGATE_TYPE = "BASECAMP";
    private static final String APPROVED = "BASECAMP_APPROVED";
    private static final String REJECTED = "BASECAMP_REJECTED";
    private static final String CONFIRMED = "BASECAMP_CONFIRMED";
    private static final String SEAT_RELEASED = "PROGRAM_SEAT_RELEASED";

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private OutboxEventRecorder recorder;

    @Autowired
    private NotificationMailer mailer;

    @Autowired
    private NotificationMailProperties properties;

    @Autowired
    private NotificationMailStore mailStore;

    @Autowired
    private NotificationMailMapper mailMapper;

    @Autowired
    private WebProperties webProperties;

    @Autowired
    private TestMailSender mailSender;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void resetMailSender() {
        mailSender.reset();
    }

    @Test
    @DisplayName("[F-20] 설정이 없으면 기본값대로 메일을 한 통 보내고 보낸 시각을 기록한다")
    void sendsMailByDefaultAndRecordsSentAt() {
        // given
        Member applicant = saveActiveMember();
        recordApproved(applicant.getId(), 3L);

        // when
        publishAndSend();

        // then
        assertThat(mailSender.sent()).hasSize(1);
        MailMessage mail = mailSender.sent().get(0);
        assertThat(mail.to()).isEqualTo(applicant.getEmail());
        assertThat(mail.subject()).isEqualTo("[뫼와들] 합류 승인");
        assertThat(mail.body()).contains("/basecamps/3");
        assertThat(countSentNotifications()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-20] 이메일을 끈 종류는 알림함에만 쌓이고 메일은 없으며, 다른 종류는 여전히 보낸다")
    void turnedOffTypeIsInboxOnly() {
        // given
        Member applicant = saveActiveMember();
        disableEmail(applicant.getId(), APPROVED);
        recordApproved(applicant.getId(), 3L);
        recordRejected(applicant.getId(), 4L);

        // when
        publishAndSend();

        // then
        assertThat(countNotifications()).isEqualTo(2);
        assertThat(mailSender.sent()).hasSize(1);
        assertThat(mailSender.sent().get(0).subject()).isEqualTo("[뫼와들] 합류 거절");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT email_sent_at IS NULL FROM notification WHERE type = ?", Boolean.class, APPROVED))
                .isTrue();
    }

    @Test
    @DisplayName("[F-20] 받는 사람이 여럿인 이벤트에서 이메일을 끈 회원만 빼고 나머지에게 보낸다")
    void multiRecipientSkipsOnlyDisabledMember() {
        // given
        Member first = saveActiveMember();
        Member second = saveActiveMember();
        Member third = saveActiveMember();
        disableEmail(second.getId(), CONFIRMED);
        recordConfirmed(3L, List.of(first.getId(), second.getId(), third.getId()));

        // when
        publishAndSend();

        // then
        assertThat(countNotifications()).isEqualTo(3);
        assertThat(mailSender.sent())
                .extracting(MailMessage::to)
                .containsExactlyInAnyOrder(first.getEmail(), third.getEmail());
    }

    @Test
    @DisplayName("[F-20] 이메일 인증 전(UNVERIFIED) 회원에게는 알림만 만들고 메일은 보내지 않는다")
    void unverifiedMemberGetsNoMail() {
        // given
        Member unverified = memberRepository.saveAndFlush(aMember().build());
        recordApproved(unverified.getId(), 3L);

        // when
        publishAndSend();

        // then
        assertThat(countNotifications()).isEqualTo(1);
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("[F-20] 이메일 인증을 마친 정지(SUSPENDED) 회원에게는 제재 확정 알림 메일을 보낸다")
    void verifiedSuspendedMemberGetsMail() {
        // given
        Member suspended = saveActiveMember();
        jdbcTemplate.update("UPDATE member SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());
        recordSanctionConfirmed(suspended.getId());

        // when
        publishAndSend();

        // then
        assertThat(mailSender.sent()).hasSize(1);
        assertThat(mailSender.sent().get(0).to()).isEqualTo(suspended.getEmail());
    }

    @Test
    @DisplayName("[F-20] 이메일 인증을 마치지 않은 정지(SUSPENDED) 회원에게는 알림만 만들고 메일은 보내지 않는다")
    void unverifiedSuspendedMemberGetsNoMail() {
        // given
        Member suspended = memberRepository.saveAndFlush(aMember().build());
        jdbcTemplate.update("UPDATE member SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());
        recordSanctionConfirmed(suspended.getId());

        // when
        publishAndSend();

        // then
        assertThat(countNotifications()).isEqualTo(1);
        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("[F-20] 이벤트를 발행할 때는 알림만 저장하고 메일은 보내지 않으며, 메일은 발송 작업이 보낸다")
    void publishingStoresNotificationsWithoutSendingMail() {
        // given
        Member first = saveActiveMember();
        Member second = saveActiveMember();
        recordConfirmed(3L, List.of(first.getId(), second.getId()));

        // when
        publisher.publishPending(OutboxLane.DEFAULT);

        // then
        assertThat(eventStatus()).isEqualTo("PUBLISHED");
        assertThat(mailSender.sent()).isEmpty();
        assertThat(countByEmailStatus("PENDING")).isEqualTo(2);

        // when
        int sent = mailer.sendDue();

        // then
        assertThat(sent).isEqualTo(2);
        assertThat(mailSender.sent())
                .extracting(MailMessage::to)
                .containsExactlyInAnyOrder(first.getEmail(), second.getEmail());
        assertThat(countByEmailStatus("SENT")).isEqualTo(2);
        assertThat(countSentNotifications()).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-20][NFR-04] 메일 한 통이 실패하면 그 알림만 재시도 간격 뒤에 다시 보내고, 받는 회원마다 메일은 한 통이다")
    void failedSendIsRetriedOnlyForThatNotification() {
        // given
        Member first = saveActiveMember();
        Member second = saveActiveMember();
        recordConfirmed(3L, List.of(first.getId(), second.getId()));
        mailSender.failNextSends(1);

        // when
        publishAndSend();

        // then
        assertThat(eventStatus()).isEqualTo("PUBLISHED");
        assertThat(mailSender.sent()).hasSize(1);
        assertThat(countByEmailStatus("SENT")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT email_attempt_count FROM notification WHERE email_status = 'PENDING'", Integer.class))
                .isEqualTo(1);

        // when
        int beforeRetryDelay = mailer.sendDue();

        // then
        assertThat(beforeRetryDelay).isZero();

        // when
        clock.advance(properties.retryDelay());
        mailer.sendDue();

        // then
        assertThat(countNotifications()).isEqualTo(2);
        assertThat(mailSender.sent())
                .extracting(MailMessage::to)
                .containsExactlyInAnyOrder(first.getEmail(), second.getEmail());
        assertThat(countByEmailStatus("SENT")).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-20] 재시도 한도만큼 실패한 메일은 FAILED로 두고 더 보내지 않는다")
    void mailFailingUpToMaxAttemptsBecomesFailed() {
        // given
        Member applicant = saveActiveMember();
        recordApproved(applicant.getId(), 3L);
        publisher.publishPending(OutboxLane.DEFAULT);
        mailSender.failNextSends(properties.maxAttempts());

        // when
        for (int attempt = 0; attempt < properties.maxAttempts(); attempt++) {
            mailer.sendDue();
            clock.advance(properties.retryMaxDelay());
        }
        int afterFailed = mailer.sendDue();

        // then
        assertThat(afterFailed).isZero();
        assertThat(mailSender.sent()).isEmpty();
        assertThat(countByEmailStatus("FAILED")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT email_attempt_count FROM notification", Integer.class))
                .isEqualTo(properties.maxAttempts());
    }

    @Test
    @DisplayName("[F-20] 메일이 실패할 때마다 다음 시도까지의 간격이 두 배로 늘고 상한에서 멈춘다")
    void retryDelayDoublesOnEachFailureUpToMaxDelay() {
        // given
        int failures = properties.maxAttempts() - 1;
        Member applicant = saveActiveMember();
        recordApproved(applicant.getId(), 3L);
        publisher.publishPending(OutboxLane.DEFAULT);
        mailSender.failNextSends(failures);
        List<Duration> delays = new ArrayList<>();

        // when
        for (int attempt = 0; attempt < failures; attempt++) {
            mailer.sendDue();
            Duration delay = Duration.between(clock.instant(), nextAttemptAt());
            delays.add(delay);
            clock.advance(delay);
        }

        // then
        assertThat(delays).isEqualTo(expectedDelays(failures));
        assertThat(delays).contains(properties.retryMaxDelay());
        assertThat(countByEmailStatus("PENDING")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-20] 메일 서버에 연결하지 못한 일시적인 실패는 한도를 넘어도 FAILED로 두지 않고, 서버가 돌아오면 보낸다")
    void transientFailureIsRetriedBeyondMaxAttemptsUntilItIsSent() {
        // given
        int failures = properties.maxAttempts() + 2;
        Member applicant = saveActiveMember();
        recordApproved(applicant.getId(), 3L);
        publisher.publishPending(OutboxLane.DEFAULT);
        mailSender.failNextSendsTransiently(failures);

        // when
        for (int attempt = 0; attempt < failures; attempt++) {
            mailer.sendDue();
            clock.advance(properties.retryMaxDelay());
        }

        // then
        assertThat(countByEmailStatus("PENDING")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT email_attempt_count FROM notification", Integer.class))
                .isEqualTo(failures);

        // when
        mailer.sendDue();

        // then
        assertThat(countByEmailStatus("SENT")).isEqualTo(1);
        assertThat(mailSender.sent()).extracting(MailMessage::to).containsExactly(applicant.getEmail());
    }

    @Test
    @DisplayName("[F-20] 일시적인 실패도 알림을 만든 지 일시 실패 재시도 기간(1일)이 지났으면 FAILED로 굳히고, 기간 안의 알림은 PENDING으로 둔다")
    void transientFailureBecomesFailedOnlyAfterRetryWindow() {
        // given
        recordApproved(saveActiveMember().getId(), 3L);
        publisher.publishPending(OutboxLane.DEFAULT);
        clock.advance(Duration.ofSeconds(1));
        recordApproved(saveActiveMember().getId(), 4L);
        publisher.publishPending(OutboxLane.DEFAULT);
        clock.advance(properties.transientRetryWindow().minusSeconds(1));
        mailSender.failNextSendsTransiently(2);

        // when
        int handled = mailer.sendDue();

        // then
        assertThat(handled).isEqualTo(2);
        assertThat(countByEmailStatus("FAILED")).isEqualTo(1);
        assertThat(countByEmailStatus("PENDING")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT email_next_attempt_at IS NULL FROM notification WHERE email_status = 'FAILED'",
                        Boolean.class))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT email_next_attempt_at IS NOT NULL FROM notification WHERE email_status = 'PENDING'",
                        Boolean.class))
                .isTrue();
    }

    @Test
    @DisplayName("[F-20] 재시도 사이에 회원이 이메일을 끄면 남은 메일은 보내지 않고 SKIPPED로 둔다")
    void settingChangedBetweenRetriesIsRespected() {
        // given
        Member applicant = saveActiveMember();
        recordApproved(applicant.getId(), 3L);
        mailSender.failNextSends(1);
        publishAndSend();

        // when
        disableEmail(applicant.getId(), APPROVED);
        clock.advance(properties.retryDelay());
        mailer.sendDue();

        // then
        assertThat(mailSender.sent()).isEmpty();
        assertThat(countByEmailStatus("SKIPPED")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-20] 발송 작업이 집은 메일은 임대가 끝나기 전에는 다른 발송 작업이 다시 집지 않는다")
    void claimedMailIsNotClaimedAgainBeforeLeaseEnds() {
        // given
        Member applicant = saveActiveMember();
        recordApproved(applicant.getId(), 3L);
        publisher.publishPending(OutboxLane.DEFAULT);
        long notificationId = jdbcTemplate.queryForObject("SELECT id FROM notification", Long.class);
        jdbcTemplate.update(
                "UPDATE notification SET email_next_attempt_at = ? WHERE id = ?",
                java.sql.Timestamp.from(clock.instant().plus(properties.lease())),
                notificationId);

        // when
        int beforeLeaseEnds = mailer.sendDue();
        clock.advance(properties.lease());
        int afterLeaseEnds = mailer.sendDue();

        // then
        assertThat(beforeLeaseEnds).isZero();
        assertThat(afterLeaseEnds).isEqualTo(1);
        assertThat(mailSender.sent()).hasSize(1);
    }

    @Test
    @DisplayName("[F-20][NFR-04] 앱이 종료를 시작하면 발송 작업은 보내던 메일 한 통만 마치고 멈추고, 보내지 않은 메일은 임대를 풀어 다음 발송 작업이 바로 집는다")
    void stopsAfterCurrentMailOnShutdownAndReleasesLeaseOfUnsentMails() {
        // given
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            memberIds.add(saveActiveMember().getId());
        }
        recordConfirmed(3L, memberIds);
        publisher.publishPending(OutboxLane.DEFAULT);
        ShutdownSignal signal = new ShutdownSignal();
        List<MailMessage> sentByStoppingMailer = new ArrayList<>();
        MailSender shutdownStartingSender = message -> {
            sentByStoppingMailer.add(message);
            signal.request();
        };
        NotificationMailer stoppingMailer =
                new NotificationMailer(mailStore, shutdownStartingSender, clock, webProperties, signal);

        // when
        int handled = stoppingMailer.sendDue();

        // then
        assertThat(handled).isEqualTo(1);
        assertThat(sentByStoppingMailer).hasSize(1);
        assertThat(countByEmailStatus("SENT")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM notification WHERE email_status = 'PENDING' AND email_next_attempt_at IS NULL",
                        Integer.class))
                .isEqualTo(2);

        // when
        int afterRestart = mailer.sendDue();

        // then
        assertThat(afterRestart).isEqualTo(2);
        assertThat(mailSender.sent()).hasSize(2);
        assertThat(countByEmailStatus("SENT")).isEqualTo(3);
    }

    @Test
    @DisplayName("[F-20][NFR-04] 종료가 이미 시작됐으면 발송 작업은 메일을 집지 않아서 임대도 걸리지 않는다")
    void doesNotClaimMailsAfterShutdownStarted() {
        // given
        recordApproved(saveActiveMember().getId(), 3L);
        publisher.publishPending(OutboxLane.DEFAULT);
        ShutdownSignal signal = new ShutdownSignal();
        signal.request();
        NotificationMailer stoppedMailer = new NotificationMailer(mailStore, mailSender, clock, webProperties, signal);

        // when
        int handled = stoppedMailer.sendDue();

        // then
        assertThat(handled).isZero();
        assertThat(mailSender.sent()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM notification WHERE email_next_attempt_at IS NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-20][NFR-04] 임대가 끝난 뒤 다른 발송 작업이 다시 집은 메일은, 처음 집은 발송 작업이 임대를 풀려 해도 풀리지 않는다")
    void releaseLeaseKeepsMailsThatAnotherSenderClaimedAgain() {
        // given
        recordApproved(saveActiveMember().getId(), 3L);
        publisher.publishPending(OutboxLane.DEFAULT);
        long notificationId = jdbcTemplate.queryForObject("SELECT id FROM notification", Long.class);
        NotificationMailStore.Claim first = mailStore.claim(clock.instant());
        clock.advance(properties.lease().plusSeconds(1));
        NotificationMailStore.Claim second = mailStore.claim(clock.instant());

        // when
        int releasedByFirst = mailStore.releaseLease(first.leaseUntil(), List.of(notificationId));

        // then
        assertThat(releasedByFirst).isZero();
        assertThat(nextAttemptAt()).isEqualTo(second.leaseUntil());

        // when
        int releasedBySecond = mailStore.releaseLease(second.leaseUntil(), List.of(notificationId));

        // then
        assertThat(releasedBySecond).isEqualTo(1);
        assertThat(mailStore.claim(clock.instant()).targets()).hasSize(1);
    }

    @Test
    @DisplayName("[F-20][NFR-04] 임대가 끝난 뒤 다른 발송 작업이 다시 집은 메일에, 처음 집은 발송 작업이 발송 완료를 써도 PENDING으로 남고 다시 집은 작업의 임대가 유지된다")
    void staleLeaseHolderCannotMarkSent() {
        // given
        recordApproved(saveActiveMember().getId(), 3L);
        publisher.publishPending(OutboxLane.DEFAULT);
        long notificationId = jdbcTemplate.queryForObject("SELECT id FROM notification", Long.class);
        NotificationMailStore.Claim first = mailStore.claim(clock.instant());
        clock.advance(properties.lease().plusSeconds(1));
        NotificationMailStore.Claim second = mailStore.claim(clock.instant());

        // when
        int updated = mailMapper.markSent(notificationId, first.leaseUntil(), clock.instant());

        // then
        assertThat(updated).isZero();
        assertThat(countByEmailStatus("PENDING")).isEqualTo(1);
        assertThat(countSentNotifications()).isZero();
        assertThat(nextAttemptAt()).isEqualTo(second.leaseUntil());
    }

    @Test
    @DisplayName("[F-20][NFR-04] 임대가 끝난 뒤 다른 발송 작업이 다시 집은 메일에, 처음 집은 발송 작업이 실패를 써도 시도 횟수와 임대가 바뀌지 않는다")
    void staleLeaseHolderCannotMarkFailedAttempt() {
        // given
        recordApproved(saveActiveMember().getId(), 3L);
        publisher.publishPending(OutboxLane.DEFAULT);
        long notificationId = jdbcTemplate.queryForObject("SELECT id FROM notification", Long.class);
        NotificationMailStore.Claim first = mailStore.claim(clock.instant());
        clock.advance(properties.lease().plusSeconds(1));
        NotificationMailStore.Claim second = mailStore.claim(clock.instant());

        // when
        int updated = mailMapper.markFailedAttempt(
                notificationId, first.leaseUntil(), false, properties.retryPolicy(), clock.instant());

        // then
        assertThat(updated).isZero();
        assertThat(countByEmailStatus("PENDING")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT email_attempt_count FROM notification", Integer.class))
                .isZero();
        assertThat(nextAttemptAt()).isEqualTo(second.leaseUntil());
    }

    @Test
    @DisplayName("[F-20][PG-07] 빈자리 알림은 모두 알림함에 저장하고, 메일은 알림을 먼저 신청한 순서로 받을 수 있는 회원 50명까지만 보낸다")
    void seatReleasedMailIsLimitedToFirstFiftyDeliverableMembers() {
        // given
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < 55; i++) {
            members.add(saveActiveMember());
        }
        Member disabled = members.get(1);
        disableEmail(disabled.getId(), SEAT_RELEASED);
        Member unverified = memberRepository.saveAndFlush(aMember().build());
        List<Long> memberIds = new ArrayList<>();
        memberIds.add(unverified.getId());
        members.forEach(member -> memberIds.add(member.getId()));
        record(SEAT_RELEASED, 7L, Map.of("programId", 7L, "memberIds", memberIds));

        // when
        publisher.publishPending(OutboxLane.DEFAULT);
        for (int i = 0; i < 5; i++) {
            mailer.sendDue();
        }

        // then
        assertThat(countNotifications()).isEqualTo(56);
        List<String> expected = members.stream()
                .filter(member -> !member.getId().equals(disabled.getId()))
                .limit(50)
                .map(Member::getEmail)
                .toList();
        assertThat(mailSender.sent()).extracting(MailMessage::to).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(countByEmailStatus("SENT")).isEqualTo(50);
        assertThat(countByEmailStatus("SKIPPED")).isEqualTo(6);
    }

    @Test
    @DisplayName("[F-20][NFR-04] 발송 작업 두 개가 동시에 보내도 알림마다 메일은 한 통이다")
    void twoSendersSendEachMailOnce() throws Exception {
        // given
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            memberIds.add(saveActiveMember().getId());
        }
        recordConfirmed(3L, memberIds);
        publisher.publishPending(OutboxLane.DEFAULT);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Integer> drain = () -> {
            start.await();
            int total = 0;
            int sent;
            do {
                sent = mailer.sendDue();
                total += sent;
            } while (sent > 0);
            return total;
        };

        // when
        try {
            Future<Integer> first = executor.submit(drain);
            Future<Integer> second = executor.submit(drain);
            start.countDown();
            int total = first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS);

            // then
            assertThat(total).isEqualTo(30);
        } finally {
            executor.shutdownNow();
        }
        assertThat(mailSender.sent()).hasSize(30);
        assertThat(mailSender.sent()).extracting(MailMessage::to).doesNotHaveDuplicates();
        assertThat(countByEmailStatus("SENT")).isEqualTo(30);
    }

    @Test
    @DisplayName("[F-20] 최근 24시간에 보낸 알림 메일이 상한(300통)에 닿으면 남은 자리만큼만 보내고, 나머지는 알림함에 두고 보내기를 미룬다")
    void mailsBeyondDailyLimitStayPendingWhileInboxKeepsAll() {
        // given
        int remaining = 3;
        List<Long> memberIds = saveActiveMembers(5);
        insertSentNotifications(
                memberIds.get(0), NotificationEventTypes.EMAIL_LIMIT_PER_WINDOW - remaining, Duration.ofHours(1));
        recordConfirmed(3L, memberIds);
        publisher.publishPending(OutboxLane.DEFAULT);

        // when
        int handled = drain();

        // then
        assertThat(handled).isEqualTo(remaining);
        assertThat(mailSender.sent()).hasSize(remaining);
        assertThat(countByEmailStatus("SENT")).isEqualTo(NotificationEventTypes.EMAIL_LIMIT_PER_WINDOW);
        assertThat(countByEmailStatus("PENDING")).isEqualTo(2);
        assertThat(countByEmailStatus("SKIPPED")).isZero();
        assertThat(countByEmailStatus("FAILED")).isZero();
        assertThat(countNotifications()).isEqualTo(NotificationEventTypes.EMAIL_LIMIT_PER_WINDOW - remaining + 5);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM notification WHERE email_status = 'PENDING'"
                                + " AND email_attempt_count = 0 AND email_next_attempt_at IS NULL",
                        Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("[F-20] 상한 때문에 미룬 알림 메일은 이미 보낸 메일이 24시간 기간에서 빠지면 알림 순서대로 나간다")
    void deferredMailsGoOutWhenSentMailsLeaveTheWindow() {
        // given
        List<Long> memberIds = saveActiveMembers(5);
        insertSentNotifications(
                memberIds.get(0), NotificationEventTypes.EMAIL_LIMIT_PER_WINDOW - 3, Duration.ofHours(1));
        recordConfirmed(3L, memberIds);
        publisher.publishPending(OutboxLane.DEFAULT);
        drain();
        mailSender.reset();

        // when
        clock.advance(Duration.ofHours(22));
        int insideWindow = drain();

        // then
        assertThat(insideWindow).isZero();
        assertThat(mailSender.sent()).isEmpty();

        // when
        clock.advance(Duration.ofHours(2).plusSeconds(1));
        int afterWindow = drain();

        // then
        assertThat(afterWindow).isEqualTo(2);
        assertThat(mailSender.sent()).hasSize(2);
        assertThat(countByEmailStatus("PENDING")).isZero();
    }

    @Test
    @DisplayName("[F-20] 기간(24시간) 밖에서 보낸 알림 메일은 상한에 세지 않는다")
    void mailsSentBeforeTheWindowDoNotCountTowardLimit() {
        // given
        List<Long> memberIds = saveActiveMembers(5);
        insertSentNotifications(
                memberIds.get(0),
                NotificationEventTypes.EMAIL_LIMIT_PER_WINDOW,
                NotificationEventTypes.EMAIL_LIMIT_WINDOW.plusMinutes(1));
        recordConfirmed(3L, memberIds);
        publisher.publishPending(OutboxLane.DEFAULT);

        // when
        int handled = drain();

        // then
        assertThat(handled).isEqualTo(5);
        assertThat(mailSender.sent()).hasSize(5);
    }

    private List<Long> saveActiveMembers(int count) {
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            memberIds.add(saveActiveMember().getId());
        }
        return memberIds;
    }

    private void insertSentNotifications(long memberId, int count, Duration sentAgo) {
        Timestamp sentAt = Timestamp.from(clock.instant().minus(sentAgo));
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new Object[] {memberId, "seed:" + i, sentAt, sentAt});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO notification (member_id, type, title, body, dedup_key, email_status,"
                        + " email_attempt_count, email_sent_at, created_at)"
                        + " VALUES (?, 'BASECAMP_CONFIRMED', '확정', '확정됐어요.', ?, 'SENT', 0, ?, ?)",
                rows);
    }

    private int drain() {
        int total = 0;
        int sent;
        do {
            sent = mailer.sendDue();
            total += sent;
        } while (sent > 0);
        return total;
    }

    private Member saveActiveMember() {
        Member member = memberRepository.saveAndFlush(aMember().build());
        jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE', email_verified_at = updated_at WHERE id = ?", member.getId());
        return member;
    }

    private void disableEmail(long memberId, String type) {
        jdbcTemplate.update(
                "INSERT INTO notification_setting (member_id, type, email_enabled) VALUES (?, ?, FALSE)",
                memberId,
                type);
    }

    private void recordApproved(long applicantId, long basecampId) {
        record(APPROVED, basecampId, Map.of("applicationId", 1L, "applicantId", applicantId, "basecampId", basecampId));
    }

    private void recordSanctionConfirmed(long memberId) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("memberId", memberId);
        payload.put("sanctionId", 1L);
        payload.put("sanctionType", "PERMANENT");
        payload.put("endsAt", null);
        record("SANCTION_CONFIRMED", memberId, payload);
    }

    private void recordRejected(long applicantId, long basecampId) {
        record(REJECTED, basecampId, Map.of("applicationId", 2L, "applicantId", applicantId, "basecampId", basecampId));
    }

    private void recordConfirmed(long basecampId, List<Long> memberIds) {
        record(CONFIRMED, basecampId, Map.of("basecampId", basecampId, "memberIds", memberIds));
    }

    private void record(String eventType, long aggregateId, Map<String, Object> payload) {
        transactionTemplate.executeWithoutResult(
                status -> recorder.record(eventType, AGGREGATE_TYPE, aggregateId, payload));
    }

    private void publishAndSend() {
        publisher.publishPending(OutboxLane.DEFAULT);
        mailer.sendDue();
    }

    private int countByEmailStatus(String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE email_status = ?", Integer.class, status);
    }

    private int countNotifications() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification", Integer.class);
    }

    private int countSentNotifications() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE email_sent_at IS NOT NULL", Integer.class);
    }

    private String eventStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM outbox_event", String.class);
    }

    private Instant nextAttemptAt() {
        return jdbcTemplate
                .queryForObject("SELECT email_next_attempt_at FROM notification", Timestamp.class)
                .toInstant();
    }

    private List<Duration> expectedDelays(int failures) {
        List<Duration> expected = new ArrayList<>();
        Duration delay = properties.retryDelay();
        for (int attempt = 0; attempt < failures; attempt++) {
            expected.add(delay);
            delay = min(delay.multipliedBy(2), properties.retryMaxDelay());
        }
        return expected;
    }

    private static Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }
}

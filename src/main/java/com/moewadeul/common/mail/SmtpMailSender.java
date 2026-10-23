package com.moewadeul.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP로 메일을 보낸다. 연결·읽기·쓰기 제한 시간은 {@code spring.mail.properties.mail.smtp.*} 설정이 정하고, 이 값은 SMTP 명령 하나마다 따로 걸린다.
 * 메일 한 통에는 응답을 기다리는 명령이 열 번 넘게 있어서, 서버가 명령마다 제한 시간 가까이 늦게 답하면 한 통이 1분 가까이 걸릴 수 있다.
 * 그래서 한 통 전체에도 {@code moewadeul.mail.send-timeout}으로 상한을 둔다. 앱이 종료를 시작하면 Spring은 실행 중인 작업을 30초까지만 기다리는데,
 * 이 상한이 그보다 짧아야 보내던 메일 한 통이 그 안에 끝난다. 상한을 넘으면 일시적인 실패로 처리하고 나중에 다시 보낸다. 상한을 넘겼어도 서버가
 * 이미 메일을 받아들였을 수 있어서, 같은 메일이 한 번 더 나갈 수 있다.
 *
 * <p>발신 주소는 {@code moewadeul.mail.from}이고, 설정이 없으면 SMTP 계정({@code spring.mail.username})을 쓴다.
 * Gmail은 로그인한 계정과 다른 발신 주소를 계정 주소로 바꿔 보내기 때문이다. 받는 사람의 메일함에는 주소 대신
 * {@code moewadeul.mail.from-name}에 둔 서비스 이름이 보인다.
 *
 * <p>실패 원인에는 수신자 주소가 들어 있을 수 있어서, 예외 메시지에는 원인의 클래스 이름만 남긴다.
 *
 * <p>메일 서버에 연결하지 못했거나(연결 거절, 제한 시간 초과 같은 입출력 예외) 서버가 4xx로 답한 실패는 일시적인 실패로 표시한다.
 * SMTP에서 4xx는 "나중에 다시 보내라"는 뜻이고, 5xx는 같은 메일을 다시 보내도 받지 않는다는 뜻이기 때문이다.
 */
@Component
public class SmtpMailSender implements MailSender, DisposableBean {

    // 메일 구현체(Angus Mail)는 SMTP 응답 줄을 예외 메시지에 그대로 넣는다. 응답 코드를 읽는 클래스는 실행할 때만 있는 의존성이라
    // 컴파일할 때 쓸 수 없다. 그래서 메시지 맨 앞의 세 자리 숫자로 4xx 응답인지 판단한다.
    private static final Pattern TRANSIENT_REPLY = Pattern.compile("^4\\d\\d\\b");

    private final JavaMailSender javaMailSender;
    private final String from;
    private final String fromName;
    private final Duration sendTimeout;
    // 가상 스레드에서 보내면 상한을 넘겨 취소할 때 인터럽트가 소켓 읽기·쓰기를 끊는다. 플랫폼 스레드는 소켓 입출력에서 인터럽트로 깨어나지 못한다.
    private final ExecutorService sender = Executors.newVirtualThreadPerTaskExecutor();

    public SmtpMailSender(
            JavaMailSender javaMailSender,
            @Value("${moewadeul.mail.from}") String from,
            @Value("${moewadeul.mail.from-name}") String fromName,
            @Value("${moewadeul.mail.send-timeout}") Duration sendTimeout) {
        this.javaMailSender = javaMailSender;
        this.from = from;
        this.fromName = fromName;
        this.sendTimeout = sendTimeout;
    }

    @Override
    public void send(MailMessage message) {
        try {
            sendWithinTimeout(toMimeMessage(message));
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            throw new MailDeliveryException(
                    "mail delivery failed: " + e.getClass().getSimpleName(), e, isTransient(e));
        }
    }

    private void sendWithinTimeout(MimeMessage mimeMessage) {
        Future<?> sending = sender.submit(() -> javaMailSender.send(mimeMessage));
        try {
            sending.get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            sending.cancel(true);
            throw new MailDeliveryException("mail delivery timed out", e, true);
        } catch (InterruptedException e) {
            sending.cancel(true);
            Thread.currentThread().interrupt();
            throw new MailDeliveryException("mail delivery interrupted", e, true);
        } catch (ExecutionException e) {
            throw rethrown(e.getCause());
        }
    }

    // 보내는 스레드에서 난 예외를 호출한 스레드에서 그대로 다시 던진다. 호출하는 쪽의 예외 변환이 예전과 같게 동작한다.
    private static RuntimeException rethrown(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(failure);
    }

    @Override
    public void destroy() {
        sender.shutdownNow();
    }

    // Spring은 연결 실패를 원인 예외로 감싸고, 메시지별 전송 실패는 원인 없이 getFailedMessages()에 담는다.
    // 그래서 원인 사슬과 메시지별 실패를 모두 따라간다. 같은 예외를 두 번 보지 않도록 본 예외를 기억한다.
    static boolean isTransient(Exception failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> pending = new ArrayDeque<>();
        pending.push(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.pop();
            if (!seen.add(current)) {
                continue;
            }
            if (isTransientCause(current)) {
                return true;
            }
            pushRelated(current, pending);
        }
        return false;
    }

    private static boolean isTransientCause(Throwable failure) {
        // 발신자 이름을 인코딩하지 못한 실패도 IOException이지만, 설정이 틀린 것이라 다시 보내도 같다.
        if (failure instanceof IOException && !(failure instanceof UnsupportedEncodingException)) {
            return true;
        }
        String message = failure.getMessage();
        return message != null && TRANSIENT_REPLY.matcher(message.strip()).find();
    }

    private static void pushRelated(Throwable failure, Deque<Throwable> pending) {
        if (failure.getCause() != null) {
            pending.push(failure.getCause());
        }
        if (failure instanceof MailSendException sendFailure) {
            sendFailure.getFailedMessages().values().forEach(pending::push);
        }
    }

    private MimeMessage toMimeMessage(MailMessage message) throws MessagingException, UnsupportedEncodingException {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, StandardCharsets.UTF_8.name());
        helper.setFrom(from, fromName);
        helper.setTo(message.to());
        helper.setSubject(message.subject());
        helper.setText(message.body(), false);
        return mimeMessage;
    }
}

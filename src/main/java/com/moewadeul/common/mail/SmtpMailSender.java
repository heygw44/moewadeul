package com.moewadeul.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

// JavaMail의 연결·읽기·쓰기 제한 시간은 SMTP 명령마다 따로 걸린다. 그래서 한 통 전체에 send-timeout 상한을 따로 둔다.
// SMTP에서 4xx는 나중에 다시 보내라는 뜻이고, 5xx는 다시 보내도 받지 않는다는 뜻이다.
@Component
public class SmtpMailSender implements MailSender, DisposableBean {

    // 메일 구현체(Angus Mail)는 SMTP 응답 줄을 예외 메시지에 그대로 넣는다. 응답 코드를 읽는 클래스는 실행할 때만 있는 의존성이라
    // 컴파일할 때 쓸 수 없다. 그래서 메시지 맨 앞의 세 자리 숫자로 4xx 응답인지 판단한다.
    private static final Pattern TRANSIENT_REPLY = Pattern.compile("^4\\d\\d\\b");

    private final JavaMailSender javaMailSender;
    private final MailProperties properties;
    // 가상 스레드에서 보내면 상한을 넘겨 취소할 때 인터럽트가 소켓 읽기·쓰기를 끊는다. 플랫폼 스레드는 소켓 입출력에서 인터럽트로 깨어나지 못한다.
    private final ExecutorService sender = Executors.newVirtualThreadPerTaskExecutor();

    public SmtpMailSender(JavaMailSender javaMailSender, MailProperties properties) {
        this.javaMailSender = javaMailSender;
        this.properties = properties;
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
            sending.get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
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
        helper.setFrom(properties.from(), properties.fromName());
        helper.setTo(message.to());
        helper.setSubject(message.subject());
        helper.setText(message.body(), false);
        return mimeMessage;
    }
}

package com.moewadeul.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 비교가 끝난 직후에 요청 하나를 멈춰 세우는 테스트 도구다. 서버는 비교하는 동안 DB 트랜잭션을 열지 않으므로,
 * 비교 전에 읽은 값과 비교 뒤에 하는 일 사이에 다른 요청이 끼어드는 순서를 테스트가 정해 만들 수 있다.
 *
 * <p>{@link #arm}으로 멈출 비밀번호 원문을 정하면, 그 원문으로 비교한 첫 호출이 비교를 마친 뒤 {@link #release}를 부를 때까지 멈춘다.
 * 한 번 멈춘 뒤에는 같은 원문으로 비교해도 멈추지 않는다. 테스트가 끝나면 {@link #clear}로 비운다.
 */
public final class PasswordMatchPause {

    private static final long TIMEOUT_SECONDS = 60;

    private static volatile String armedPassword;
    private static volatile CountDownLatch paused = new CountDownLatch(1);
    private static volatile CountDownLatch resume = new CountDownLatch(1);

    private PasswordMatchPause() {}

    public static synchronized void arm(String rawPassword) {
        paused = new CountDownLatch(1);
        resume = new CountDownLatch(1);
        armedPassword = rawPassword;
    }

    /** 호출하면 멈춘 요청이 비교를 마치고 멈출 때까지 기다린다. */
    public static void awaitPaused() throws InterruptedException {
        assertThat(paused.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    /** 호출하면 멈춘 요청이 이어서 실행된다. */
    public static void release() {
        resume.countDown();
    }

    public static synchronized void clear() {
        armedPassword = null;
        resume.countDown();
    }

    private static void pauseIfArmed(Object rawPassword) {
        String armed = armedPassword;
        if (armed == null || !armed.contentEquals((CharSequence) rawPassword)) {
            return;
        }
        synchronized (PasswordMatchPause.class) {
            if (armedPassword == null) {
                return;
            }
            armedPassword = null;
        }
        paused.countDown();
        try {
            assertThat(resume.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // 인코더는 인터페이스를 구현한 빈이라 같은 인터페이스의 프록시로 감싼다. matches를 부른 뒤에만 멈춘다.
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        static BeanPostProcessor passwordMatchPausePostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof PasswordEncoder)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            bean.getClass().getClassLoader(),
                            new Class<?>[] {PasswordEncoder.class},
                            (proxy, method, args) -> {
                                Object result;
                                try {
                                    result = method.invoke(bean, args);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                                if (method.getName().equals("matches")) {
                                    pauseIfArmed(args[0]);
                                }
                                return result;
                            });
                }
            };
        }
    }
}

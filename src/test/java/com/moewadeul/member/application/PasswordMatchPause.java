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

    public static void awaitPaused() throws InterruptedException {
        assertThat(paused.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

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

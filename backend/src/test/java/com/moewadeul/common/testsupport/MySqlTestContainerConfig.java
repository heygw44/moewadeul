package com.moewadeul.common.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

// 컨테이너를 빈으로 두면, 같은 설정을 쓰는 통합 테스트가 Spring 컨텍스트 캐시와 함께 컨테이너 하나를 재사용한다.
// 반면 @Testcontainers/@Container를 쓰면 JUnit이 클래스마다 컨테이너를 멈추므로, 캐시된 컨텍스트와 충돌한다.
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestContainerConfig {

    // 운영 MySQL과 같은 시작 옵션을 준다. binlog가 켜진 MySQL은 SUPER 권한이 없는 계정의 트리거 생성을 거부하는데,
    // 마이그레이션이 지도 묶음 집계용 트리거를 만들기 때문이다.
    @Bean
    @ServiceConnection
    MySQLContainer mysql() {
        return new MySQLContainer("mysql:8.4").withCommand("--log-bin-trust-function-creators=1");
    }
}

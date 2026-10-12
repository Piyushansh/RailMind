package com.railmind.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Redis-backed lockout actually works against a real Redis
 * instance (Testcontainers), not just that the code compiles against the
 * StringRedisTemplate API. Requires Docker to be available locally.
 *
 * Deliberately does NOT boot the real application context - that needs a
 * live Postgres (DataSource/Flyway/JPA), which is irrelevant to what this
 * test is verifying. Instead it boots a minimal context with only Redis
 * auto-configuration, explicitly excluding the DB-related auto-configs so
 * this test has no dependency on Postgres being available at all.
 */
@Testcontainers
@SpringBootTest(classes = LoginAttemptServiceIT.MinimalRedisTestConfig.class)
class LoginAttemptServiceIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    static class MinimalRedisTestConfig {
        // Intentionally empty: no component scanning of com.railmind.* here, so this
        // context only ever contains what Redis's own auto-configuration provides.
    }

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    void locksOutAfterFiveFailures_andClearsOnSuccess() {
        LoginAttemptService service = new LoginAttemptService(redisTemplate);
        String email = "brute-force-test@railmind.demo";

        assertThat(service.isLocked(email)).isFalse();

        for (int i = 0; i < 4; i++) {
            service.recordFailure(email);
            assertThat(service.isLocked(email)).isFalse(); // not locked until the 5th failure
        }

        service.recordFailure(email); // 5th failure
        assertThat(service.isLocked(email)).isTrue();

        service.recordSuccess(email);
        assertThat(service.isLocked(email)).isFalse(); // success clears the lockout immediately
    }
}

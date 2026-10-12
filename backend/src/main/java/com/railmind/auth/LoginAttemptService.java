package com.railmind.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed brute-force guard: locks an email out for a cooldown window
 * after too many failed logins. Backed by Redis specifically so this works
 * correctly across multiple backend instances (a horizontally-scaled
 * deployment sharing one Redis) instead of the previous per-instance
 * in-memory version, which would let an attacker bypass the lockout just by
 * hitting a different instance.
 *
 * Implementation: a single counter key per email with a sliding TTL.
 * - recordFailure: INCR the counter; on the first failure in a window, set a
 *   15-minute TTL. Once the counter reaches MAX_ATTEMPTS, the key's
 *   continued existence (until TTL expiry) IS the lockout - isLocked() just
 *   checks the counter value.
 * - recordSuccess: DEL the counter, clearing the lockout immediately.
 */
@Component
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "railmind:login-attempts:";

    private final StringRedisTemplate redisTemplate;

    public LoginAttemptService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isLocked(String email) {
        String value = redisTemplate.opsForValue().get(key(email));
        if (value == null) return false;
        try {
            return Integer.parseInt(value) >= MAX_ATTEMPTS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void recordFailure(String email) {
        String redisKey = key(email);
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            // first failure in this window - start the TTL clock now
            redisTemplate.expire(redisKey, LOCKOUT_WINDOW);
        }
    }

    public void recordSuccess(String email) {
        redisTemplate.delete(key(email));
    }

    private String key(String email) {
        return KEY_PREFIX + (email == null ? "" : email.toLowerCase());
    }
}
